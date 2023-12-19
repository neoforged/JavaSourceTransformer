import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class OrderedWorkQueue implements AutoCloseable {
    private static final int MAX_PENDING_WORK = 25;
    private final Deque<Future<WorkResult>> pending = new ArrayDeque<>(MAX_PENDING_WORK);
    private final ZipOutputStream zout;

    public OrderedWorkQueue(ZipOutputStream zout) {
        this.zout = zout;
    }

    public void submit(ZipEntry entry, byte[] content) throws InterruptedException, IOException {
        if (pending.isEmpty()) {
            // Can write directly if nothing else is pending
            zout.putNextEntry(entry);
            zout.write(content);
            zout.closeEntry();
        } else {
            // Needs to be queued behind currently queued async work
            drainTo(MAX_PENDING_WORK - 1);
            pending.add(CompletableFuture.completedFuture(new WorkResult(entry, content)));
        }
    }

    public void submitAsync(ZipEntry entry, Supplier<byte[]> contentSupplier) throws InterruptedException, IOException {
        drainTo(MAX_PENDING_WORK - 1);
        pending.add(CompletableFuture.supplyAsync(() -> new WorkResult(entry, contentSupplier.get())));
    }

    private void drainTo(int drainTo) throws InterruptedException, IOException {
        while (pending.size() > drainTo) {
            WorkResult workResult;
            try {
                workResult = pending.removeFirst().get();
            } catch (ExecutionException e) {
                if (e.getCause() instanceof IOException ioe) {
                    throw ioe;
                }
                throw new RuntimeException(e.getCause());
            }
            zout.putNextEntry(workResult.entry);
            zout.write(workResult.content);
            zout.closeEntry();
        }
    }

    @Override
    public void close() throws IOException {
        try {
            drainTo(0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            zout.close();
        }
    }

    record WorkResult(ZipEntry entry, byte[] content) {
    }
}
