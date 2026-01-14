# Bailiwick File Definitions

A description of how files within the Bailiwick Network are laid out and formatted.

All files go in the Bailiwick base directory. The location of the base directory may differ based on device, OS and preferences, but within the chosen file system location, the bw directory structure will follow this layout.

## Versions
All files for a given file structure version (which differs from the application version) will live at
bw/<file layout version>/
E.g. files for version 0.1 will be in bw/0.1/…

## Identity
An Identity is a description of a User. Identities can be managed per group. e.g. Users have multiple Identities. 

A User’s public Identity lives at bw/<version>/identity 
Users’ other Identities are linked to from within other files.

### 0.1
Format: JSON File
Encryption: None
Required: Yeahbut…
Layout: 
{
  “name”: “Public Name”,
  “profilePic”: CID,
}

All values are optional. E.g. if a User desires, their fields may be empty references. In such a case, the User has decided to forgo any specific identification at this level. If present, higher-level Identification data will be used to fill in gaps. E.g. a Public Name will be used in Group-level Identities which do not provide a different Name.

Public identification is optionally used for searches and during Introductions.

## Subscriptions
The Subscriptions file contains an encrypted list of all the peers we subscribe to and which personal social circle they belong to

It lives at bw/<version>/subs

### 0.1
Format: JSON File
Encryption: RSA w/Poster’s Private Key
Required: Yes
Layout:
{
    “peers”: [ peer_id1, peer_id2, …]
    “circles”: { “circle_1”: [peer_id1, peer_id13], “circle_2”: [peer_id_2, peer_id3]...}
}

## Manifest
The Manifest lists the set of posts that a User created. The Manifest is further subdivided by each Group the User creates. 

It lives at bw/<version>/feed

### 0.1
Format: JSON File
Encryption: AES w/Poster’s Basic Key
Required: Yes
Layout:
{
  	“feeds”: [ <ipfs/group1>, <ipfs/group2>, … ],
	“interactions”: <ipfs/interactions>,
	“identity”: <cid>
}


**Fields**:
feeds: A list of links to Feed files (below)
interactions: A link to an Interaction file (below)
identity: Link to “All-Subscribers” Identity.

## Feeds
Each Group a User creates has its own Feed file. These are linked to from the Top Level Feed.

It lives at <unique cid>

### 0.1
Format: JSON File
Encryption: AES w/Poster’s Group-Specific Key
Required: Yes
Layout: 
{
	“updated_at”: <unix timestamp>
  	“posts”: [ <cid1>, <cid2>, … ],
	“interactions”: [<cid1>, <cid2>],
	“actions”: [<cid1>, <cid2>]
	“identity”: <cid>
}

Only the past 30 days (by default) are included in the list. As posts age out, they will be removed from the list.


## Posts
A Post represents content uploaded by a User.

Posts live only at their respective CIDs and are linked to from a Feed file.

A Post file consists of metadata regarding the Post (time posted, content type, etc) and the data contained in the Post itself. A Post may contain a utf-8 text string and/or linked Files of any general mime type. E.g. various formats of text, image, video, or just a random binary file.

Posts may have a parent_cid which will link to another Post. A Post with a valid parent_cid is considered a “Comment” and will only be made visible in the Client if the entire chain of Posts from the top to here are visible to the Client. In practical terms, this means that a Comment is only visible if its parent_cid is either None or visible to the Client.

The Post metadata maintains a list of the CIDs of the various Files (if any) attached to the Post. 

A File is represented by a tuple of mimetype and CID.

Posts will appear in multiple Group Feeds if multiple Groups are allowed to view the Post. It will be encrypted with each appropriate key and appear in each list. Hence a signature field is present to aid in deduplication post retrieval.

### 0.1
Format: JSON File
Encryption: AES w/Poster’s Group-Specific Key
Required: Yes
Layout: 
{

	“timestamp”: <unix utc timestamp>,
	“parent_cid”: <CID> | None
	“text”: “You might be a 90’s kid if…”,
	“files”: [ [“image/jpeg”, <CID1>],
 [“image/jpeg”, <CID2>], 
 [“image/jpeg”, <CID3>],... ],
	“signature”: <Hash of post without this member>
}


## Files
A File is raw, uploaded data. It could be a picture, an executable, a book, whatever. It has no added metadata.

Files do not have to be encrypted, but the default is to do so. In some cases it may be beneficial to upload a File in an unencrypted fashion. i.e. when uploading the latest version of an open-source executable, it may be better to keep it unencrypted, the better to share it. Then others can “reshare” the same File without adding superfluous copies to the DHT.

Files have no maximum size.

### 0.1
Format: <bytes>
Encryption: Optional AES w/Poster’s Group-Specific Key
Required: No
Layout: <bytes>

## Interactions
An Interaction represents a User’s interaction with another User’s Post or Interaction. An Interaction can take the form of a Reaction (an Emoji response to a Post) or a Tag (a text Tag to a Post).

Interactions may appear in multiple Group lists if multiple Groups are allowed to view the Interaction. It will be encrypted with each appropriate key and appear in each list. Hence the signature field to perform disambiguation.

### 0.1
Format: JSON File
Encryption: AES w/Poster’s Group-Specific Key
Required: No
Layout: 
{
	“type”: “reaction” | “tag”
	“content”: <utf-8 string>
	“post_cid”: <cid>
	“parent_cid”: <cid>
	“files”: [<Files>]
	“signature”: <sig>
}

## Reactions
A Reaction represents an Emoji applied by a User onto a Post or Comment.


### Content
Enough data for a single utf-8 emoji character. Extra data will be ignored.
Emojis will be whitelisted from a common set and extended as necessary.


## Tags
A Tag is a single word or short phrase that can be used within clients to automatically filter or search for content. E.g. all “nsfw”, etc tagged content can be automatically filtered out of one’s feeds by default, or all aggregated into another “special time” feed.

Tags can be applied to Posts or Comments

## Content
A short utf-8 string. Fewer than 100 utf-8 characters.


## Actions
Actions represent behind-the-scenes requests sent from one User to a Group of Users. They may be directed to individual Users or to the entire Group. Actions are a form of RPC sent to Subscribers to the User’s content.


### 0.1
Format: JSON File
Encryption: AES w/Target-Specific Key. This may be the target User’s public key or the appropriate Group key. Clients will have to try them all.
Required: No
Layout: 
{
	“type”: <enum string>
	“data”: <json struct>
	“signature”: <sig>
}

## Action Types
Actions are used for many things. Here’s what we’ve got so far:
### Delete CID
Encryption Key: Group Key
Action Type: “delete”
Action Data: { “cid”: <cid> }

Request deletion of a particular CID. Clients obeying this request will unpin and delete any cached copy they may have and then add the CID to the Client’s block list. CIDs may only be deleted if the request comes from the owner. e.g. my client must have a record of your client publishing the data, OR the CID deletion request comes from the Bailiwick blocklist. This list is published unencrypted and contains lists of CIDs blocked for legal reasons OR CIDs of trackers which will identify your personal CID. Users found downloading/spreading the blocklist files will be blocked by the bailiwick.space gateway.


### Update Key
Encryption Key: Target User’s public key
Action Type: “update_key”
Action Data: { “key”: <base64-encoded key>, “algo”: <encryption algorithm name> }

Updating security measures and/or removal of an individual from a Group requires updating the encryption key for new Posts to the Group. New Keys will be sent individually to each User, encrypted with their respective public keys.


### Introduce
Encryption Key: Target User’s public key
Action Type: “Introduce”
Action Data: { “other_parties”: [<Peer Id1>, <Peer Id2>] }

