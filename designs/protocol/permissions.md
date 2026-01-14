# Permissions and Groups

As all posts are publicly available and distributed across uncontrolled devices, we need some form of encryption to ensure that distributed data is only accessible by authorized Users.

When Authenticating a Requesting User’s access, a Posting User may grant access to a private key which can be used to decrypt a post. Said keys must also be made available in a safe manner, however, so they must also be encrypted. 

In the case of a key exchange, this may be handled either directly or indirectly.

In a direct key exchange, a secure connection is made between the two devices using ssh or similar and the encryption key is transmitted directly from the Posting User to the Requesting User.

In an indirect key exchange, the Posting User accesses the Requesting User’s public key (publicly available at some known URI) and uses it to encrypt the Secret Key, which is then sent as a DM to the Requesting User. The Requesting User’s client may then decrypt the Secret Key using their Private Key. The decrypted key may then be added to their local keyring and used when downloading Posts from the Posting User.

By selecting different keys with which Posts are encrypted a Posting User may thus divide their posts’ audiences into whatever groups they desire. Requesting Users in each group will be able to only decrypt Posts directed to their group(s).



