package portfolioboss.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import portfolioboss.api.request.CashMovementRequest;
import portfolioboss.api.request.InvestorRequest;
import portfolioboss.api.response.AddedInvestorResponse;
import portfolioboss.api.response.CashMovementResponse;
import portfolioboss.api.response.InvestorResponse;
import portfolioboss.calculation.CashMovementType;
import portfolioboss.calculation.Holding;
import portfolioboss.calculation.PortfolioSnapshot;
import portfolioboss.db.PortfolioSyncService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What the investor endpoints store, read back the way the UI reads it: through {@link PortfolioReadService}, against
 * the PostgreSQL test database ({@code portfolioboss_test}), each test rolled back — same setup as
 * {@code HoldingWriteServiceTest}. IB reports 25,000 in cash.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({PortfolioSyncService.class, PortfolioReadService.class, InvestorWriteService.class})
class InvestorWriteServiceTest {

    private static final String ACCOUNT = "U1234567";
    private static final Instant SYNCED_AT = Instant.parse("2026-09-21T08:00:00Z");
    private static final long MISSING_ID = 999_999_999L;

    @Autowired
    private PortfolioSyncService syncService;

    @Autowired
    private PortfolioReadService readService;

    @Autowired
    private InvestorWriteService writeService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    /** Nothing is served before a sync, the investors included. */
    @BeforeEach
    void syncApple() {
        Holding apple = new Holding("AAPL", 265598, "STK", "USD", 10.0, 150.0, 200.0, 2000.0, 500.0, 0.0, ACCOUNT);
        syncService.sync(new PortfolioSnapshot(ACCOUNT, SYNCED_AT, 100_000.0, 25_000.0, List.of(apple)));
    }

    // ── investors ───────────────────────────────────────────────────────────────────────────────

    @Test
    void anAddedInvestorIsServedAfterTheAccountOwnerWithACardOfTheirOwn() {
        AddedInvestorResponse avi = writeService.addInvestor(new InvestorRequest("  Avi "));

        assertThat(avi.id()).isPositive();
        assertThat(avi.name()).isEqualTo("Avi");
        assertThat(readInvestors()).extracting(InvestorResponse::name).containsExactly("Me", "Avi");
        InvestorResponse aviCard = readInvestor(avi.id());
        assertThat(aviCard.accountOwner()).isFalse();
        assertThat(aviCard.depositsMinusWithdrawals()).isEqualByComparingTo("0");
        assertThat(aviCard.cash()).isEqualByComparingTo("0");
        assertThat(aviCard.cashMovements()).isEmpty();
    }

    @Test
    void aNameAlreadyTakenIs409WhateverItsCase() {
        writeService.addInvestor(new InvestorRequest("Avi"));

        assertStatus(HttpStatus.CONFLICT, "An investor named avi already exists.",
                () -> writeService.addInvestor(new InvestorRequest("avi")));
        assertStatus(HttpStatus.CONFLICT, "An investor named ME already exists.",
                () -> writeService.addInvestor(new InvestorRequest("ME")));
    }

    @Test
    void theAccountOwnerCanBeRenamed() {
        writeService.renameInvestor(accountOwnerId(), new InvestorRequest(" Omri "));

        assertThat(readInvestor(accountOwnerId()).name()).isEqualTo("Omri");
        assertThat(readInvestor(accountOwnerId()).accountOwner()).isTrue();
    }

    @Test
    void renamingAnInvestorToTheirOwnNameInAnotherCaseIsNoConflict() {
        long aviId = writeService.addInvestor(new InvestorRequest("Avi")).id();

        writeService.renameInvestor(aviId, new InvestorRequest("AVI"));

        assertThat(readInvestor(aviId).name()).isEqualTo("AVI");
    }

    @Test
    void renamingAnInvestorToAnotherInvestorsNameIs409() {
        long aviId = writeService.addInvestor(new InvestorRequest("Avi")).id();

        assertStatus(HttpStatus.CONFLICT, "An investor named Me already exists.",
                () -> writeService.renameInvestor(aviId, new InvestorRequest("Me")));
    }

    @Test
    void renamingAnInvestorThatDoesNotExistIs404() {
        assertStatus(HttpStatus.NOT_FOUND, "No investor with id " + MISSING_ID,
                () -> writeService.renameInvestor(MISSING_ID, new InvestorRequest("Avi")));
    }

    // ── deposits and withdrawals ────────────────────────────────────────────────────────────────

    @Test
    void aDepositIsServedOnTheCardAndTakenFromTheAccountOwnersCash() {
        long aviId = writeService.addInvestor(new InvestorRequest("Avi")).id();

        CashMovementResponse deposit = writeService.addCashMovement(aviId, deposit("30000", "  First transfer "));

        assertThat(deposit.id()).isPositive();
        assertThat(deposit.note()).isEqualTo("First transfer");
        InvestorResponse avi = readInvestor(aviId);
        assertThat(avi.cashMovements()).singleElement().satisfies(storedDeposit -> {
            assertThat(storedDeposit.id()).isEqualTo(deposit.id());
            assertThat(storedDeposit.movementDate()).isEqualTo(LocalDate.of(2026, 1, 10));
            assertThat(storedDeposit.type()).isEqualTo(CashMovementType.DEPOSIT);
            assertThat(storedDeposit.amount()).isEqualByComparingTo("30000");
            assertThat(storedDeposit.note()).isEqualTo("First transfer");
        });
        assertThat(avi.cash()).isEqualByComparingTo("30000");
        assertThat(readInvestor(accountOwnerId()).cash()).isEqualByComparingTo("-5000");   // 25,000 − 30,000
    }

    @Test
    void aWithdrawalTakesMoneyOutOfTheirCash() {
        long aviId = writeService.addInvestor(new InvestorRequest("Avi")).id();
        writeService.addCashMovement(aviId, deposit("30000", null));

        writeService.addCashMovement(aviId, new CashMovementRequest(LocalDate.of(2026, 2, 1),
                CashMovementType.WITHDRAWAL, new BigDecimal("5000"), null));

        assertThat(readInvestor(aviId).depositsMinusWithdrawals()).isEqualByComparingTo("25000");
        assertThat(readInvestor(aviId).cash()).isEqualByComparingTo("25000");
    }

    /** INVESTORS_TODO.md, decision 3: the account owner's cash comes from IB. */
    @Test
    void aDepositForTheAccountOwnerIs400() {
        assertStatus(HttpStatus.BAD_REQUEST,
                "The account owner's cash comes from IB, so no deposits are entered for them.",
                () -> writeService.addCashMovement(accountOwnerId(), deposit("1000", null)));
    }

    @Test
    void aDepositForAnInvestorThatDoesNotExistIs404() {
        assertStatus(HttpStatus.NOT_FOUND, "No investor with id " + MISSING_ID,
                () -> writeService.addCashMovement(MISSING_ID, deposit("1000", null)));
    }

    @Test
    void changingADepositReplacesEveryField() {
        long aviId = writeService.addInvestor(new InvestorRequest("Avi")).id();
        long movementId = writeService.addCashMovement(aviId, deposit("30000", "First transfer")).id();
        forgetWhatHibernateLoaded();

        writeService.changeCashMovement(movementId, new CashMovementRequest(LocalDate.of(2026, 1, 12),
                CashMovementType.WITHDRAWAL, new BigDecimal("500"), "  "));

        assertThat(readInvestor(aviId).cashMovements()).singleElement().satisfies(movement -> {
            assertThat(movement.movementDate()).isEqualTo(LocalDate.of(2026, 1, 12));
            assertThat(movement.type()).isEqualTo(CashMovementType.WITHDRAWAL);
            assertThat(movement.amount()).isEqualByComparingTo("500");
            assertThat(movement.note()).isNull();
        });
    }

    @Test
    void deletingADepositTwiceIs404TheSecondTime() {
        long aviId = writeService.addInvestor(new InvestorRequest("Avi")).id();
        long movementId = writeService.addCashMovement(aviId, deposit("30000", null)).id();
        forgetWhatHibernateLoaded();

        writeService.deleteCashMovement(movementId);

        assertThat(readInvestor(aviId).cashMovements()).isEmpty();
        assertStatus(HttpStatus.NOT_FOUND, "No deposit or withdrawal with id " + movementId,
                () -> writeService.deleteCashMovement(movementId));
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /** Every request is its own transaction in the app; this makes the next read come from the database. */
    private void forgetWhatHibernateLoaded() {
        entityManager.flush();
        entityManager.clear();
    }

    private List<InvestorResponse> readInvestors() {
        forgetWhatHibernateLoaded();
        return readService.currentPortfolio().orElseThrow().investors();
    }

    private InvestorResponse readInvestor(long investorId) {
        return readInvestors().stream().filter(investor -> investor.id() == investorId).findFirst().orElseThrow();
    }

    /** The investor V4__investors.sql created. */
    private long accountOwnerId() {
        return jdbc.queryForObject("select id from investor where is_account_owner", Long.class);
    }

    private CashMovementRequest deposit(String amount, String note) {
        return new CashMovementRequest(LocalDate.of(2026, 1, 10), CashMovementType.DEPOSIT, new BigDecimal(amount),
                note);
    }

    private void assertStatus(HttpStatus expectedStatus, String expectedReason, Runnable write) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, write::run);
        assertThat(exception.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(exception.getReason()).isEqualTo(expectedReason);
    }
}
