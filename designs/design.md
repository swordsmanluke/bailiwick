# Bailiwick Design

## Overview
Bailiwick is a fully-decentralised P2P social network application. It uses a DHT to keep track of shared, encrypted file blobs. Sharing a post, image, video or other file consists of publishing the file (encrypted with the poster’s encryption key) to the DHT. Like BitTorrent, files are chunked and distributed across the network such that files are stored redundantly and can be downloaded from multiple parties simultaneously. This helps to distribute load and avoid hotspots.

Posts and updates are delivered via an RSS-style feed which can be accessed by contacts. Any given Poster’s feed can be decrypted only by approved contacts. A User’s published data is reached via a known DHT key which points to a branching structure containing their other data.

Chunks are automatically deleted from the DHT based on the age of most recent access time. More popular content will end up cached across more nodes in the network, while less popular content will be cached only in small, graph-local portions of the network. (e.g. a group of contacts may share and cache one-another’s posted content, but if not accessed outside their group, it will likely not require caching on other user’s devices. OTOH, content posted publicly by a heavily-accessed Poster may be cached on devices which have not directly downloaded the full file themselves, simply to improve distribution of content across the network.)

## Goals

### Semi-Anonymous
Posters are not required to reveal their identity, but posts are non-repudiable.

### P2P
No centralized hardware is required anywhere in the system. Everything runs on mobile hardware.
Due to geo-location IP Addresses must be kept secure, to prevent stalking

### Pull-based
Everything from posts to comments is only retrieved at the User’s wish. A new Contact wishing to transmit content to another User must have their consent before it will be retrieved. E.g. brigading even a public figure can only happen if the User in question accepts all inbound comments.

### Encrypted
All chunks in the DHT are encrypted - and signed using one of the Poster’s private keys.
Decrypting chunks requires access to post-specific(?) keys which can only be retrieved via secure key-exchange with the Poster.

Lowering access permissions for a Post (e.g. removing someone from a Group) will cause old, cached posts to be deleted from the DHT and redistribution of newly re-encrypted content for “Live” posts.

### Fine-Grained Permissions
Access to any or all posts may be granted or denied to all, specific groups or specific users. Posts affected by these permissions may require generation of new post-specific encryption keys.
This allows, e.g., removing access to past posts for a specific user in a group.

### Full Data Ownership
Posters can delete or modify past posts at any time. While other Users may have downloaded or otherwise captured past posts, individuals to whom access was not granted should be unable to capture it for marketing or other purposes.

**IOW:** You own your data. If you delete it - the network will forget it. If you change it - the network will forget the old version. If you decide you’re not friends with Tammy anymore because that bitch is dating Justin even though she knows you like him - the network will deny Tammy access to your posts (eventually).

### Ease of Use
Transmitting files of any given type between any two (or more) Users must be simple.
Maintains De-Centralization of Network

Via Licensing or other means, ensure control of the network remains decentralized. (e.g. if you want to connect to the network, legally, your client must be Open Source and may not collect/parse/sniff/use User data)

### Account Recovery - maybe
You can reclaim an account if sufficient friends agree to transfer ownership. Past posts may be lost, but the social graph and identity can be recovered.

## Anti-Goals

### Surveillance Capitalism
Since this system is entirely P2P, there are no hosting costs. Thus, there’s nothing to pay for but my own time. As an Open-Source project, this should allow it to be run for “free”.

### Harassment
No User should be able to transmit content to another User without their consent.

### Bullshit Amplification
No simple, direct “sharing” of another’s content. (Enforced via fingerprinting within the DHT?)
If you want to e.g. re-share a link someone sent to you, you must create your own post containing the relevant information.

Or possibly, allow resharing, but also allow a “recall” function for the original poster or someone in the middle to announce a retraction?

Or use signing to track the original poster of the content as it gets shared? Has the benefit of not requiring multiple copies of the same chunks in the system and makes later “social repudiation” options simpler… Like, “Hey, you shared this a while ago and I thought you’d like to know it came from a Russian Malware Farm” or something… 

### Heuristics based on Stickiness
Check in with your family when you want. Don’t “bump” controversial posts for “engagement”.

### Open Questions
* How does the relationship graph get stored? If it’s in the DHT, it’s going to be easy to scrape. If it’s not in the DHT, it’s going to be easy to lose.

* Can relationship graph be protected - at all?
* Relationship graph is stored individually. I know who my friends/subscribers are. External clients can only see my graph if I choose to publish it.

* Graph can be stored in local DB cache, but also written encrypted to the DHT. Can decrypt it with a private key.
What about no graph? When you become friends, your device permanently stores their NodeId. Accessing their content via that NodeId to find new posts is the entirety of your "graph".

* You cannot search across friends of friends to find the one “Tang Chan” you went to high school with without the cooperation of the devices on the network. Might be ok. Works like a flood search. Theoretically, after 6 steps, you could find anyone on the network.

* Bad actor constantly publishes posts to try to saturate the network's data / storage
Iroh already handles this
How would an encrypted RSS work?

* Every user has a dedicated manifest (announced via Gossip) that lists their current posts. The last, say, 100 are maintained in the manifest. Deletion happens automatically at the discretion of each client.

* How should throttling work?
May not be necessary, as it's mostly handled at the Iroh-level
How will Android devices feel about turning into servers?
Seems to be OK...

* How to ensure proper distribution of popular content to avoid hotspots?
Existing DHT algorithms split files into ‘chunks’, which are then distributed. The distribution is usually based on the signature of the chunk, which should be evenly distributed.

* DDOS protection for individual devices?
Can we hide IPs from non-friends?
Iroh protects itself from some types of attack, but you could still point a botnet at a single peer and flood them.

* As the network grows, traffic will grow exponentially - how to keep it manageable?
Bundle network update messages?

* How to join the network initially?
foo.baliwick-network.com to find first friend “foo” by DNSLink

* How to find friends?
FOAF records to enable searches within a friend network?
Request from A -> A.friends.map({b: b.friends}), etc. As long as any given node has “FOAF searching” enabled, the search may progress.

  * What if you don’t want to be found?
Can you hide from specific people/classes of people? Or just binary all or nothing?
When you Block someone, they can’t decrypt your content - what if someone refers to you? Do we hide their content as well? Encrypt with both your keys? How to order the keys?
No referrals? You can’t say @so-and-so? Or you can, but it will link to any @so-and-so the readers know?

* How are Post encryption-keys managed?
How to protect privacy within Rando Request messages? The recipient could trace the network back and generate random social network traces? Perhaps accumulate enough information to unmask identities?

* How to poll for updates in a secure manner? 

* How do a Subscriber and Poster coordinate publishing and retrieving in a way that protects both ends’ privacy?

# Glossary of Concepts

## Technology

### Network: The set of computing devices communicating together to form Bailiwick.

### DHT: Distributed Hash Table - a distributed information store which is used as the main data store within the Network.

## Actors
* User: An individual actor within the Network.
* Client: A program working on behalf of a User to communicate with the Network.
* Originating User: The User who first added a Post to the Network.

* Subscriber: A User who has received permission to download Posts from another User.

## Data
* Post: Content added to the Network.
* Chunk: A Chunk is a section of data of N-bytes stored in the DHT or cached by a client. It is likely encrypted. Posts and other data are decomposed into Chunks before storage.

## Relationships
* Subscription: A unidirectional permission to access data
* Contacts: The list of all of a User’s Subscribers.
* Friend: A User which shares a bidirectional Subscription pair with the Originating User.
* Trusted Friend: A Friend which has been given special permissions to control another User’s account. Particularly around Public Key updates.
* Circle: A group of Subscribers a User may choose to publish a Post to. A subset of Contacts.
* Cluster: A group of Clients with bidirectional Subscription agreements. Typically, a group of IRL friends/family. Clients who do not have bidirectional Subscription agreements with all other members are not part of a Cluster.
* Rando: A Client with at least 2 degrees of separation from another Client.

## Encryption
* Key or Shared Key: Unless otherwise specified, a secret key used for encryption. Typically shared between parties with access to certain content.
* Private/Public Key: Part of a Key Pair which is used to sign messages/generate shared access messages.
* Message Key: The key used to encrypt a message. May be either a Shared key or a Private key.

## API Messages
* Subscription Request: A request from one User to access the Posts of another User.

## Privacy
* IP Addresses should not be associated with specific Users, since geo-ip tracking of a mobile phone could be dangerously revealing.

Within the DHT, updates from users are stored at DHT-addresses and are not directly linked to the Client’s IPs.

Upon receiving a Chunk, a client must be unable to determine the Originating User. That linking should only be possible if they have been granted access to the chunk’s contents by the Originating User. 

## Identity
Identity is provided via Public Key/Private Key. Public Keys are linked to a UUID in the DHT. In Iroh, this is managed via the user's NodeId. The user's public key always points to a record containing information about the client, including content the client may retrieve.

Keys may be updated by a plurality of a User’s chosen Trusted Friends. A Key Update message must be signed by this plurality, whose signatures are compared to a previously generated/updated Trusted Signatories list. 

A User may provide a dedicated About Post containing Identity Metadata (e.g. name, location, age, job, etc) according to an Open (FOAF?) format. This Post will be stored at a consistent key in the DHT. Something like “UserId/Identity.foaf”?


### The Feed
The top level of all User content publishing is their Feed. The Feed file contains links to
Group-specific RSS documents containing links to Posts.
An Interactions “feed” document
An optional FOAF doc for public/semi-public identification
Timestamps associated with each

{
	"feeds": [ <cid/group1>, <cid/group2>, … ],
	"interactions": <cid/interactions>,
	"identity": <cid/foo.foaf?>
}

```
Not sure if an RSS feed is actually the best idea for us. RSS gives advantages in readers by showing a small portion of the whole. In this case, we always want to download the entire post. Given that, the <description> fields, etc are useless.

Might be best to just return a literal list of links and a timestamp. e.g.
{
"posts":["cid/1", "cid/2", ...],
"updated": <unixutcts>
}
```


### Group Feeds
Each Feed for a Group uses a different set of encryption keys. When someone is removed from a group, the keys must be updated. In this case, a new Group (with new keys) is created under the hood and the old Group is retired. The new Group will have the OG set of Posts added to it, re-encrypted with the new keys. Then, new “GroupKey” messages will be sent to all clients in the group.

The Group feed is an RSS document pointing to a list of Posts. This example is in XML, but could just as easily be in Turtle format.

<?xml version="1.0" encoding="UTF-8" ?>
<rss version="2.0">
<channel>
 <link>cid/UUID</link>
 <description>UUID</description>
 <copyright>2021 User All rights reserved</copyright>
 <lastBuildDate>Mon, 06 Sep 2020 00:01:00 +0000 </lastBuildDate>
 <ttl>1800</ttl>

 <item>
  <description>post</description>
  <link>cid/UUID</link>
  <pubDate>Sun, 06 Sep 2009 16:20:00 +0000</pubDate>
 </item>

</channel>
</rss>



### Posts
A Post consists of metadata regarding the Post (time posted, content type, etc) and the data contained in the Post itself. A Post may contain utf-8 text and/or linked Files of any general mime type. E.g. various formats of text, image, video, or just a random binary file.

The Post metadata maintains a list of the IDs of the various Files (if any) belonging to the Post. 

A File is represented by a tuple of mimetype, CID, and Signature. Though the CID is, itself, a hash of the contents, the Signature in the File is a Signature of the contents generated with the user’s private key - this should make any hypothetical hash collision attack even more challenging since the attack would need to be stable in two different algorithms.

Ex: (JSON for simplicity, but all of this is still under design and should not be taken as gospel)

{
	“version”: “0.1”
	“timestamp”: <unix utc timestamp>,
	“text”: “You might be a 90’s kid if…”,
	“files”: [ [“image/jpeg”, <CID1>, <Sig1>],
 [“image/jpeg”, <CID2>, <Sig2>], 
 [“image/jpeg”, <CID3>, <Sig3>],... ],
	“signature”: <Signature of hash without this member>
}

All Post’s metadata will be stored encrypted, pointed to by a User’s Group-specific RSS feed.

Exception: Public Posts will be published in the clear, but signed by the original poster.


## Post Signature
A Post’s “Signature” value is calculated as follows: A string consisting of the Post object’s values should be concatenated and signed. First the version, then timestamp (as the number of milliseconds), then text, then files sorted alphabetically by a concatenation of mimetype, UUID and signature. This could look something like this pseudo-Ruby code: 

post.signature = “#{post.version}#{post.timestamp}#{post.text}” + post.files.map { |f| 
  “#{f.mimeType}#{f.uuid}#{f.signature}}
}.sort.join(“”).sign(pubkey)


## Interactions
Any Subscriber may interact with a Post they have access to. (Their interactions, however, will only be visible to those who are subscribed to both the Reacting User and the Posting User’s content.)
Interactions may be of multiple types:
* Comment: Create a text/image/video response to the original Post
* Reaction: Assign an Emoji (or any unicode codepoint, really) as your emotional reaction to the Post.
* Tag: Used by Clients to Filter unwanted content or to search / aggregate desired content, this allows Users to add custom text tags to a Post in order to identify content for others. E.g. “nsfw”, “science”, “sports”, etc. Maybe at some point we can automatically identify suggested tags based on Post content at Post creation.

Interactions appear in a User’s feed as well and take form like this:

Interaction Format
{
	“type”: “comment”,
	“version”: “0.1”,
	“id”: <UUID>,
	“timestamp”: <unix utc timestamp>,
	“post”: <post_id>,
	“parent”: <comment_id or blank if not a reply to another comment>,
	“files”: [ <File1>, …],
	“signature”: <signature>
}


## Interaction Files
Files on Interactions are stored in the same format as Files on a Post. Unlike a Post, the entirety of the content of an Interaction is stored in Files referenced in the Interaction body. The text of a Comment, for instance, could be stored in a “plain/text” File. A Reaction, would be similarly stored, but the File would be very, very small. 

The contents are stored separately in order to ensure that, while any Subscriber can see the list of Interactions, they may only see the contents thereof if they _also_ have access to the original Post.

The mechanism by which this occurs is still an open question, but I have some thoughts:
* The thinking here is to find a way to make comments only readable by people with access to both the original Post and the Commenting User.
* Maybe we could generate an encryption key from the decrypted contents of the Post? Then use that to encrypt the Comment's contents? The retrieving User can then download the comment, download the original Post and then can only retrieve the decryption key if the original Post is one they have access to.
* Overcomplicated?


### Subscriptions
A Subscription models a unidirectional permission to access data. A Subscription allows one User access to another Originating User’s Post(s). An Originating User may share a Post to a Circle of selected Contacts. In this case, a Subscriber will not know about any Posts which they were not given access to. This is managed by an encrypted RSS feed, updates to which can be retrieved by a Subscriber. Subscribers can be included in multiple Circles.

When an Originating User moves a Subscriber between Circles, the Subscriber is not alerted, directly. Instead, they will receive a new set of keys which will grant access to the contents of the new Circle(s).


### Subscribing to another User
In order to subscribe to other users' content, your client needs to know the other user's public key identity. In order to provide access to another user, you must know their public key in order to give them keys. Subscriptions, then, come down to the exchange of public identifiers. In Iroh, the public id of a user is their NodeId (derived from their Ed25519 public key). Using Iroh's Gossip protocol, manifest updates are announced to subscribers.
Decentralized out-of-band Subscription
There’s not a convenient and privacy-protecting way to create a decentralized lookup, especially for a User’s first subscription. So what if we generate a single-use token that sends identifying information out of band in a QR code or similar? 

The encoded data could be something like:
{
  uid: <ipns id>
  name: “John Q Public”
  msg_id: <uuid>
  signature: <signature of uid+msg_id+name>
}

msg_id is a non-repeating identifier used to ensure that QR codes are not re-used.

Once the QR code is imported to the client, the ipns id is used to locate the user, their public key and any public identity information. The public key is used to validate the signature in the invite. The client application then prompts the User to send a confirmation in response. This will be a reciprocative QR code containing their public ID. 

1) Alice generates a QR code containing her ID and name and emails/displays/texts it to Bob.
2) Bob imports the QR code into his Bailiwick client which triggers the “Subscribe” flow
3) Bob’s client asks him if he wants to allow “Alice” to subscribe to his posts.
4) Bob (having indicated ‘yes’) is given a QR code of his own to return out-of-band to Alice.
5) Bob returns his QR code to Alice.
6) Alice imports the QR code to her Bailiwick client


### (Semi)Centralized Subscription
Using TXT records, one can point a DNS domain/subdomain at a particular IPNS location. 
Bailiwick could provide e.g. <username>.bailiwick.space records for a small annual fee. Of course, people could use their own domain records as easily.

Subscribing would then consist of exchanging FQDNs containing the appropriate TXT record.

