/*
 * Copyright (C) McEvoy Software Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.hashsplit4j.api;

import static com.sleepycat.persist.model.Relationship.MANY_TO_ONE;

import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.SecondaryKey;

/**
 * Represents a hash prefix (ie the first n digits) common to a list of hashes,
 * and the hash of the text formed by those hashes.
 * 
 * For example, assume the following hashes were inserted into the blobstore: 
 *      0123456 
 *      012345c 
 *      0125432 
 *      cce2345 
 *      cceeeee
 * 
 * Then, assuming n=3, there will be 2 root groups - 012 and cce.
 * 
 * The 012 group would contain 2 groups - 012345 and 012543
 * 
 * This forms a hierarchy as follows: root (the blobstore itself) - first level
 * groups - second level groups - actual blobs
 * 
 * The BlobStore itself and each group has a hash. The hash is formed by
 * concentrating its children in the hierarchy above with their hashes in this
 * form:
 * 
 * {name},{hash}
 * 
 * Where the name is the name of the group, and the hash is the hash of this
 * group
 * 
 * Note that if a hash exists it is assumed to be accurate. This means that
 * hashes must either be deleted or recalculated when new blobs are inserted It
 * will often be inefficient to recalculate hashes on every insertion, and would
 * be unnecessary because syncs are only occasional, so instead we assume they
 * will only be recalculated on demand.
 * 
 * E.g  +-----------+---------------+---------------+
 *      |   NAME    |   CONTENT     |   STATUS      |
 *      +-----------+---------------+---------------+
 *      |   012     |   xxxxxx      |   INVALID     |
 *      |   ccc     |   xxxxxx      |   VALID       |
 *      |   xyz     |   xxxxxx      |   INVALID     |
 *      |   abc     |   xxxxxx      |   VALID       |
 *      +-----------+---------------+---------------+
 *      
 */
@Entity
public class HashGroup {
	
    @PrimaryKey
    private String name;
    private String contentHash;
    
    @SecondaryKey(relate = MANY_TO_ONE)
    private String status; 	// Current status of group (include root group or sub group)
    						// INVALID	: Status is missing hash
    						// VALID	: status is valid 

    /**
     * Needed for deserialization
     */
    private HashGroup() {} // Scope should private

	public HashGroup(String name, String contentHash, String status) {
		this.name = name;
		this.contentHash = contentHash;
		this.status = status;
	}

    public String getName() {
        return name;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
}
