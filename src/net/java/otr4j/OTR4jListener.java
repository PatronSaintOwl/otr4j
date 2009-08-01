/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.otr4j;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionID;

/**
 * 
 * @author George Politis
 * 
 */
public interface OTR4jListener {
	public void injectMessage(SessionID sessionID, String msg);

	public void showWarning(String warning);

	public void showError(String error);

	public int getPolicy(Session ctx);

	public KeyPair getKeyPair(String account, String protocol)
			throws NoSuchAlgorithmException;
}
