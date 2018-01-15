/*
 * Copyright 2017 Cloudopt.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package net.cloudopt.next.encrypt

import java.security.MessageDigest

/*
 * @author: Cloudopt
 * @Time: 2018/1/8
 * @Description: For MD5 encryption
 */
class MD5Encrypt : Encrypt() {

    private val ALGORITHM = "MD5"

    /**
     * MD5 encryption
     * @param value This is a string that needs to be encrypted
     * @return Encrypted string
     */
    override fun encrypt(value: String): String {
        var instance:MessageDigest = MessageDigest.getInstance(ALGORITHM)
        var digest:ByteArray = instance.digest(value.toByteArray())
        return toHexString(digest)
    }

    /**
     * MD5 does not support decryption
     */
    override fun decrypt(value: String): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


}
