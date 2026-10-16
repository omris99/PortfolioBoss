package portfolioboss.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import portfolioboss.api.request.CashMovementRequest;
import portfolioboss.api.request.InvestorRequest;
import portfolioboss.api.response.AddedInvestorResponse;
import portfolioboss.api.response.CashMovementResponse;
import portfolioboss.db.InvestorCashMovementEntity;
import portfolioboss.db.InvestorCashMovementRepository;
import portfolioboss.db.InvestorEntity;
import portfolioboss.db.InvestorRepository;
import portfolioboss.utils.Utils;

/**
 * Stores the investors the user enters by hand and the deposits and withdrawals of each — PortfolioBoss's own data,
 * never Interactive Brokers'. The account owner exists from the migration on; this only adds the others. Each method is
 * one transaction; the request was already validated by the controller ({@code @Valid}), and an id that does not exist
 * is a {@code ResponseStatusException} with 404, which Spring turns into the HTTP answer with the message as its
 * {@code detail}. Also says which investor a trade is entered for ({@link #investorOfTrade}).
 */
@Service
public class InvestorWriteService {

    private final InvestorRepository investorRepository;
    private final InvestorCashMovementRepository cashMovementRepository;

    public InvestorWriteService(InvestorRepository investorRepository,
                                InvestorCashMovementRepository cashMovementRepository) {
        this.investorRepository = investorRepository;
        this.cashMovementRepository = cashMovementRepository;
    }

    @Transactional
    public AddedInvestorResponse addInvestor(InvestorRequest request) {
        String name = request.name().strip();
        if (investorRepository.existsByNameIgnoreCase(name)) {
            throw nameTaken(name);
        }
        return new AddedInvestorResponse(investorRepository.save(new InvestorEntity(name)));
    }

    /** The account owner's name too. */
    @Transactional
    public void renameInvestor(long investorId, InvestorRequest request) {
        InvestorEntity investor = findInvestor(investorId);
        String name = request.name().strip();
        if (investorRepository.existsByNameIgnoreCaseAndIdNot(name, investorId)) {
            throw nameTaken(name);
        }
        investor.changeName(name);   // Hibernate writes the change at commit
    }

    /** Never the account owner's: their cash comes from IB, so a deposit entered for them would be counted twice. */
    @Transactional
    public CashMovementResponse addCashMovement(long investorId, CashMovementRequest request) {
        InvestorEntity investor = findInvestor(investorId);
        if (investor.isAccountOwner()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The account owner's cash comes from IB, so no deposits are entered for them.");
        }
        InvestorCashMovementEntity newMovement = new InvestorCashMovementEntity(investor, request.movementDate(),
                request.type(), request.amount(), Utils.trimmedOrNull(request.note()));
        return new CashMovementResponse(cashMovementRepository.save(newMovement));
    }

    /** Every field is replaced; the movement stays the same investor's. */
    @Transactional
    public CashMovementResponse changeCashMovement(long movementId, CashMovementRequest request) {
        InvestorCashMovementEntity movement = findCashMovement(movementId);
        movement.changeDetails(request.movementDate(), request.type(), request.amount(),
                Utils.trimmedOrNull(request.note()));
        return new CashMovementResponse(movement);
    }

    @Transactional
    public void deleteCashMovement(long movementId) {
        cashMovementRepository.delete(findCashMovement(movementId));
    }

    /**
     * Who a trade is entered for: the investor the request names, or the account owner when it names none. Used by
     * {@link HoldingWriteService} and {@link ManualPositionWriteService}, so the rule is written once. An id that does
     * not exist is 400, not 404: it is a field of the request, not the address of what is written.
     */
    public InvestorEntity investorOfTrade(Long investorId) {
        if (investorId == null) {
            return investorRepository.accountOwner();
        }
        return investorRepository.findById(investorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No investor with id " + investorId));
    }

    private InvestorEntity findInvestor(long investorId) {
        return investorRepository.findById(investorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No investor with id " + investorId));
    }

    private InvestorCashMovementEntity findCashMovement(long movementId) {
        return cashMovementRepository.findById(movementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No deposit or withdrawal with id " + movementId));
    }

    /** 409 Conflict: the request is valid, but not with the investors already stored. */
    private ResponseStatusException nameTaken(String name) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "An investor named " + name + " already exists.");
    }
}
