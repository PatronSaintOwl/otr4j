/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.otr4j.session;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.interfaces.DHPublicKey;

import net.java.otr4j.OtrException;
import net.java.otr4j.crypto.OtrCryptoEngine;
import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.io.SerializationUtils;

/**
 *
 * @author George Politis
 */
class SessionKeysImpl implements SessionKeys {

	private static final Logger logger = Logger.getLogger(SessionKeysImpl.class.getName());
	private final String keyDescription;
	private final byte[] sendingCtr = new byte[16];
	private final byte[] receivingCtr = new byte[16];

	private int localKeyID;
	private int remoteKeyID;
	private DHPublicKey remoteKey;
	private KeyPair localPair;

	private byte[] sendingAESKey;
	private byte[] receivingAESKey;
	private byte[] sendingMACKey;
	private byte[] receivingMACKey;
	private Boolean isUsedReceivingMACKey;
	private BigInteger s;
	private Boolean isHigh;

	SessionKeysImpl(int localKeyIndex, int remoteKeyIndex) {

		String tmpKeyDescription;
		if (localKeyIndex == 0)
			tmpKeyDescription = "(Previous local, ";
		else
			tmpKeyDescription = "(Most recent local, ";

		if (remoteKeyIndex == 0)
			tmpKeyDescription += "Previous remote)";
		else
			tmpKeyDescription += "Most recent remote)";
		this.keyDescription = tmpKeyDescription;
	}

	@Override
	public void setLocalPair(KeyPair keyPair, int localPairKeyID) {
		this.localPair = keyPair;
		this.setLocalKeyID(localPairKeyID);
		logger.log(Level.FINEST, "{0} current local key ID: {1}",
				new Object[] {keyDescription, this.getLocalKeyID()});
		this.reset();
	}

	@Override
	public void setRemoteDHPublicKey(DHPublicKey pubKey, int remoteKeyID) {
		this.setRemoteKey(pubKey);
		this.setRemoteKeyID(remoteKeyID);
		logger.log(Level.FINEST, "{0} current remote key ID: {1}",
				new Object[] {keyDescription, this.getRemoteKeyID()});
		this.reset();
	}

	@Override
	public void incrementSendingCtr() {
		logger.log(Level.FINEST, "Incrementing counter for (localkeyID, remoteKeyID) = ({0},{1})",
				new Object[] {getLocalKeyID(), getRemoteKeyID()});
		// logger.debug("Counter prior increament: " +
		// Utils.dump(sendingCtr,
		// true, 16));
		for (int i = 7; i >= 0; i--)
			if (++sendingCtr[i] != 0)
				break;
		// logger.debug("Counter after increament: " +
		// Utils.dump(sendingCtr,
		// true, 16));
	}

	@Override
	public byte[] getSendingCtr() {
		return sendingCtr;
	}

	@Override
	public byte[] getReceivingCtr() {
		return receivingCtr;
	}

	@Override
	public void setReceivingCtr(byte[] ctr) {
		System.arraycopy(ctr, 0, receivingCtr, 0, ctr.length);
	}

	private void reset() {
		logger.log(Level.FINEST, "Resetting {0} session keys.", keyDescription);
		Arrays.fill(this.sendingCtr, (byte) 0x00);
		Arrays.fill(this.receivingCtr, (byte) 0x00);
		this.sendingAESKey = null;
		this.receivingAESKey = null;
		this.sendingMACKey = null;
		this.receivingMACKey = null;
		this.setIsUsedReceivingMACKey(false);
		this.s = null;
		if (getLocalPair() != null && getRemoteKey() != null) {
			this.isHigh = ((DHPublicKey) getLocalPair().getPublic()).getY()
					.abs().compareTo(getRemoteKey().getY().abs()) == 1;
		}

	}

	private byte[] h1(byte b) throws OtrException {

		try {
			byte[] secbytes = SerializationUtils.writeMpi(getS());

			int len = secbytes.length + 1;
			ByteBuffer buff = ByteBuffer.allocate(len);
			buff.put(b);
			buff.put(secbytes);
			byte[] result = new OtrCryptoEngineImpl().sha1Hash(buff.array());
			return result;
		} catch (IOException e) {
			throw new OtrException(e);
		}
	}

	@Override
	public byte[] getSendingAESKey() throws OtrException {
		if (sendingAESKey != null)
			return sendingAESKey;

		byte sendbyte = LOW_SEND_BYTE;
		if (this.isHigh)
			sendbyte = HIGH_SEND_BYTE;

		byte[] h1 = h1(sendbyte);

		byte[] key = new byte[OtrCryptoEngine.AES_KEY_BYTE_LENGTH];
		ByteBuffer buff = ByteBuffer.wrap(h1);
		buff.get(key);
		logger.finest("Calculated sending AES key.");
		this.sendingAESKey = key;
		return sendingAESKey;
	}

	@Override
	public byte[] getReceivingAESKey() throws OtrException {
		if (receivingAESKey != null)
			return receivingAESKey;

		byte receivebyte = LOW_RECEIVE_BYTE;
		if (this.isHigh)
			receivebyte = HIGH_RECEIVE_BYTE;

		byte[] h1 = h1(receivebyte);

		byte[] key = new byte[OtrCryptoEngine.AES_KEY_BYTE_LENGTH];
		ByteBuffer buff = ByteBuffer.wrap(h1);
		buff.get(key);
		logger.finest("Calculated receiving AES key.");
		this.receivingAESKey = key;

		return receivingAESKey;
	}

	@Override
	public byte[] getSendingMACKey() throws OtrException {
		if (sendingMACKey != null)
			return sendingMACKey;

		sendingMACKey = new OtrCryptoEngineImpl().sha1Hash(getSendingAESKey());
		logger.finest("Calculated sending MAC key.");
		return sendingMACKey;
	}

	@Override
	public byte[] getReceivingMACKey() throws OtrException {
		if (receivingMACKey == null) {
			receivingMACKey = new OtrCryptoEngineImpl()
					.sha1Hash(getReceivingAESKey());
			logger.finest("Calculated receiving AES key.");
		}
		return receivingMACKey;
	}

	private BigInteger getS() throws OtrException {
		if (s == null) {
			s = new OtrCryptoEngineImpl().generateSecret(getLocalPair()
					.getPrivate(), getRemoteKey());
			logger.finest("Calculating shared secret S.");
		}
		return s;
	}

	@Override
	public void setS(BigInteger s) {
		this.s = s;
	}

	@Override
	public void setIsUsedReceivingMACKey(Boolean isUsedReceivingMACKey) {
		this.isUsedReceivingMACKey = isUsedReceivingMACKey;
	}

	@Override
	public Boolean getIsUsedReceivingMACKey() {
		return isUsedReceivingMACKey;
	}

	private void setLocalKeyID(int localKeyID) {
		this.localKeyID = localKeyID;
	}

	@Override
	public int getLocalKeyID() {
		return localKeyID;
	}

	private void setRemoteKeyID(int remoteKeyID) {
		this.remoteKeyID = remoteKeyID;
	}

	@Override
	public int getRemoteKeyID() {
		return remoteKeyID;
	}

	private void setRemoteKey(DHPublicKey remoteKey) {
		this.remoteKey = remoteKey;
	}

	@Override
	public DHPublicKey getRemoteKey() {
		return remoteKey;
	}

	@Override
	public KeyPair getLocalPair() {
		return localPair;
	}
}
