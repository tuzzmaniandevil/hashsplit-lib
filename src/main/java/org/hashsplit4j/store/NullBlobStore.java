package org.hashsplit4j.store;

import org.hashsplit4j.api.BlobStore;

/**
 * Does nothing. Really. Nothing.
 *
 * @author brad
 */
public class NullBlobStore implements BlobStore{

    @Override
    public void setBlob(String hash, byte[] bytes) {
        
    }

    @Override
    public byte[] getBlob(String hash) {
        return null;
    }

    @Override
    public boolean hasBlob(String hash) {
        return false;
    }

}
