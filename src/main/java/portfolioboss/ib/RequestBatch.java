package portfolioboss.ib;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A batch of IB requests sent together — the two scans, the contract details of the stocks found, the daily closes of
 * many contracts —, each for one key (a scan code, a contract id), each answered with any number of answers and then an
 * end: its own end callback, or an error. The answers arrive on the reader thread while the startup thread waits, and on
 * a timeout reads what came so far — hence the concurrent collections.
 *
 * @param <Key>    what each request asks about: a scan code, a contract id
 * @param <Answer> one answer to a request: a scan's row, a contract's details, a daily close
 */
class RequestBatch<Key, Answer> {

    /** Names a request in an error line: "daily closes of contract" + " 756733". */
    private final String whatEachRequestReads;
    /** The batch's requests are numbered from here, so that no two batches share a request id. */
    private final int firstRequestId;
    private final Map<Integer, Key> keyByRequestId = new ConcurrentHashMap<>();
    private final Map<Integer, List<Answer>> answersByRequestId = new ConcurrentHashMap<>();
    private final Set<Integer> finishedRequestIds = ConcurrentHashMap.newKeySet();
    private volatile CountDownLatch requestsNotFinished = new CountDownLatch(0);

    protected RequestBatch(String whatEachRequestReads, int firstRequestId) {
        this.whatEachRequestReads = whatEachRequestReads;
        this.firstRequestId = firstRequestId;
    }

    /**
     * Called before the requests go out: gives each key a request id of its own, counting up from the batch's first
     * one in the order given, and returns them — request id to key — for the requests to be sent with.
     */
    protected Map<Integer, Key> expect(Collection<Key> keys) {
        Map<Integer, Key> keyByNewRequestId = new LinkedHashMap<>();
        int requestId = firstRequestId;
        for (Key key : keys) {
            keyByNewRequestId.put(requestId, key);
            keyByRequestId.put(requestId, key);
            answersByRequestId.put(requestId, Collections.synchronizedList(new ArrayList<>()));
            requestId++;
        }
        requestsNotFinished = new CountDownLatch(keys.size());
        return keyByNewRequestId;
    }

    /** What the request asks about, or {@code null} when the request id is not one of this batch. */
    protected Key keyOf(int requestId) {
        return keyByRequestId.get(requestId);
    }

    /** Kept only while the request is running: an answer after its end (a scan updating itself) is dropped. */
    protected void addAnswer(int requestId, Answer answer) {
        List<Answer> answers = answersByRequestId.get(requestId);
        if (answers != null && !finishedRequestIds.contains(requestId)) {
            answers.add(answer);
        }
    }

    /** Counts a request down once; {@code true} only the first time, and only for a request of this batch. */
    protected boolean finish(int requestId) {
        if (keyByRequestId.containsKey(requestId) && finishedRequestIds.add(requestId)) {
            requestsNotFinished.countDown();
            return true;
        }
        return false;
    }

    /**
     * An error ends a running request without more answers, and is printed. One that comes after the request has ended
     * — IB confirming a cancelled scan — is dropped without a line. {@code false} when the request id is not one of this
     * batch, so the error is someone else's.
     */
    protected boolean finishWithError(int requestId, int errorCode, String errorMessage) {
        Key key = keyOf(requestId);
        if (key == null) {
            return false;
        }
        if (finish(requestId)) {
            System.err.println("[ib error] " + whatEachRequestReads + " " + key + ": code=" + errorCode
                    + " — " + errorMessage);
        }
        return true;
    }

    /** Blocks until every request has ended — with its answers or with an error — or the timeout elapses. */
    protected boolean awaitAllFinished(long timeout, TimeUnit unit) throws InterruptedException {
        return requestsNotFinished.await(timeout, unit);
    }

    /**
     * By key, the answers of every request that has ended with at least one; one still running when this is called (a
     * timeout) is left out rather than served half-received.
     */
    protected Map<Key, List<Answer>> answersOfFinishedRequests() {
        Map<Key, List<Answer>> answersByKey = new LinkedHashMap<>();
        for (int requestId : finishedRequestIds) {
            List<Answer> answers = List.copyOf(answersByRequestId.get(requestId));
            if (!answers.isEmpty()) {
                answersByKey.put(keyByRequestId.get(requestId), answers);
            }
        }
        return answersByKey;
    }
}
