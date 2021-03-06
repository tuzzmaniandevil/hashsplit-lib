package org.hashsplit4j.store;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.hashsplit4j.api.BlobStore;
import org.hashsplit4j.triplets.HashCalc;
import org.slf4j.LoggerFactory;

/**
 * Implements getting and setting blobs over HTTP
 *
 * @author brad
 */
public class HABlobStore implements BlobStore {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(HABlobStore.class);

    private final BlobStore primary;
    private final BlobStore secondary;
    private final ExecutorService exService;

    private boolean trySecondaryWhenNotFound = true;
    private boolean validate = false;
    private int retries = 3;

    private BlobStore curPrimary;
    private BlobStore curSecondary;

    public HABlobStore(BlobStore primary, BlobStore secondary) {
        this.primary = primary;
        this.secondary = secondary;

        curPrimary = primary;
        curSecondary = secondary;

        exService = Executors.newFixedThreadPool(5); // 5 threads
    }

    @Override
    public void setBlob(String hash, byte[] bytes) {
        Exception last = null;
        for (int i = 0; i < retries; i++) {
            try {
                _setBlob(hash, bytes);
                return;
            } catch (Exception e) {
                log.warn("Failed to setBlob on both stores. Retry=" + i + " of " + retries, e);
                last = e;
            }
        }
        throw new RuntimeException("Failed to set to any blobstore after " + retries + " attempts", last);
    }

    private void _setBlob(String hash, byte[] bytes) throws Exception {
        BlobStore p = curPrimary;
        BlobStore s = curSecondary;
        try {
            p.setBlob(hash, bytes);
            enqueue(hash, bytes, s);
        } catch (Exception ex) {
            log.warn("setBlob failed on primary: " + p + " because of: " + ex.getMessage() + " blob size: " + bytes.length);
            log.warn("try on seconday: " + s + " ...");
            s.setBlob(hash, bytes);
            enqueue(hash, bytes, p);
            log.warn("setBlob succeeded on secondary");
            switchStores(p, s);
        }

    }

    private void enqueue(String hash, byte[] bytes, BlobStore target) {
//        log.info("enqueue: " + hash);
        InsertBlobRunnable r = new InsertBlobRunnable(hash, bytes, target);
        exService.submit(r);
    }

    @Override
    public boolean hasBlob(String hash) {
        byte[] bytes = getBlob(hash);
        return bytes != null;
    }

    @Override
    public byte[] getBlob(String hash) {
        BlobStore from;
        BlobStore p = curPrimary;
        BlobStore s = curSecondary;
        byte[] arr;
        try {
            from = p;
            arr = p.getBlob(hash);
            if (arr == null) {
                if (trySecondaryWhenNotFound && curSecondary != null) {
                    log.info("Not found in primary, and trySecondaryWhenNotFound is true, so try secondary");
                    from = s;
                    arr = s.getBlob(hash);
                }
            }
        } catch (Exception ex) {
            log.warn("getBlob failed on primary: " + p + " because of: " + ex.getMessage());
            log.warn("try on seconday: " + s + " ...");
            try {
                from = s;
                arr = s.getBlob(hash);
                log.warn("getBlob succeeded on secondary");
            } catch (Exception e) {
                throw new RuntimeException("Failed to lookup from secondary: " + s, e);
            }
            switchStores(p, s);
        }

        if (validate && (arr != null)) {
            try {
                log.trace("Validate blob with hash={} with size={}", hash, arr.length);
                HashCalc.getInstance().verifyHash(new ByteArrayInputStream(arr), hash);
            } catch (IOException ex) {
                throw new RuntimeException("Hash check failed: " + hash + " num bytes: " + arr.length + " from " + from.toString());
            }
        }

        return arr;
    }

    public boolean isTrySecondaryWhenNotFound() {
        return trySecondaryWhenNotFound;
    }

    public void setTrySecondaryWhenNotFound(boolean trySecondaryWhenNotFound) {
        this.trySecondaryWhenNotFound = trySecondaryWhenNotFound;
    }

    private synchronized void switchStores(BlobStore primary, BlobStore secondary) {
        if (secondary == null) {
            log.warn("switchStores: Cant switch because there is no configured secondary");
            // Cant switch
            return;
        }
        log.warn("Switching stores due to primary failure...");

        this.curPrimary = secondary;
        this.curSecondary = primary;
        log.warn("Done switching stores. New primary=" + curPrimary + " New seconday=" + curSecondary);
    }

    public boolean isValidate() {
        return validate;
    }

    public void setValidate(boolean validate) {
        this.validate = validate;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public class InsertBlobRunnable implements Runnable {

        private final String hash;
        private final byte[] bytes;
        private final BlobStore blobStore;

        public InsertBlobRunnable(String hash, byte[] bytes, BlobStore blobStore) {
            this.hash = hash;
            this.bytes = bytes;
            this.blobStore = blobStore;
        }

        @Override
        public void run() {
            try {
                //log.info("Insert blob in other " + hash);
                blobStore.setBlob(hash, bytes);
            } catch (Throwable e) {
                log.error("Exception inserting blob into store:" + blobStore, e);
            }
        }

    }
}
