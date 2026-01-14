# Bailiwick UX Elements


## App Pages

T = Transition to Fragment/Activity<X>
E = Event

A ‘T’ indented below an ‘E’ indicates that the Transition is triggered by that event.

### First run
T: Import Key
T: Create Account


### Create Account
E: Generate Identity
T: Content


### Import Key
E: Read key file
E: Captured user password
E: Decrypt key file
E: Create Identity
E: Delete key file
E: Download self/config
T: Content


### Content
E: Displayed selected User's posts
E: Swiped Left/Right
	T: Select Prev/Next User, reload Content
E: Tapped on Profile pic
	T: Active User -> User Profile page : Select User
E: Tapped on comment thread
E: Respond to comment thread
	T: New Comment
E: Create New Post
	T: New Post
T: Group Settings
T: Contacts
T: Send Connect Request
T: Introduce
T: Import Connection QR
T: Process Introduction



### User Profile
E: (un)Subscribe
E: (un)Subscribe to Tags
E: Edit Groups
	T: Group Settings


### New Comment
E: Wrote text
E: Selected File/Photo/Video
E: Submitted
	T: Content


### New Post
E: Wrote Text
E: Selected File/Photo/Video
E: Submitted
	T: Content


### Group Settings
E: Add/Remove User <-> Group
E: Select User
T: Content


### Contacts
E: Selected User
	T: User Profile


### Send Connect Request
E: Share QR code
T: Text/Email/etc


### Introduce
E: Select User 1
E: Select User(s) 2+
T: Content


### Import Connect QR
E: Share response code
	T: Text/Email, etc
T: Group Settings (new User)


### Process Introduction
T: Group Settings (new User(s))
T: Content



## Fragments
Android Fragments make up the pieces of the UI. We build the Pages out of these


### First Run
<two buttons>


### Create Account
<Identity info: name, pic, etc>


### Import Key
<File picker>


### Content:Feed
Shows Posts for User(s) - mostly a list of other elements


### Content:Post
User Pic/Name -> Link to User Profile
ContentView


### Content:ContentView
Image/Video/File
Text
Response bar -> Start typing to respond
Click to view/expand thread


### Content:ThreadScroll
Scroll through comments in current thread

