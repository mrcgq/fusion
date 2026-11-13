5.3.4-J62
========================
Current Good working copy after chaturbate reverting their changes. Chaturbate Video and audio currently working.
* jre bundled (in releases) (fixed permissions) (linux and macos)
* updated ffmpeg (in releases) (fixed permissions) (windows, linux and macos)
* fixed run script permissions (linux and macos)
* bump ver
* all builds checked

5.3.3-J62
========================
Reverted for chaturbate reverting their changes. Chaturbate Video and audio currently working.
* jre bundled (in releases)
* flirt4free fix - "topic" no longer sent, replaced with empty string
* bump ver
* all builds checked

5.3.0
========================
* Added menu entry to force recording of models without changing the prio
* Added blacklist and whitelist settings to automatically filter out models
* Added setting to delete orphaned recording metadata (switched off by default)
* Doubled Bongacams page size
* Fixed thumbnail caching
* Made sure, that @winkru's fix to faster check, if a Chaturbate model is
  online is merged correctly into my codebase. This should reduce 429 errors
  and speed up the online check quite a bit.
* Java 21 is now required
* Changes from @WinkRU's fork
  * Added setting to restrict recording by bit rate
  * Added setting to use the shortest side to restrict the resolution
  * Cam4: Fixed stream URLs search. Slightly increased chances to find good one.
  * Camsoda: Added "Voyeur" tab
  * Chaturbate: Added "Gaming" tab
  * Flirt4Free: Added support for new "secure" stream URLs format.
  * Streamate:
    - Fixed "Couldn't load model ID" error while adding models by URL or by
      nickname
    - Online / Offline switch on all tabs. Up to 10 000 offline models in each 
      category. How do you like it, Elon Musk?
    - Added "New Girls" tab and adjusted others. All same models on less tabs
  * Stripchat: 
    - Added "Private" tab
    - Improved Search
    - CTBRec can record your Spy/Private/Ticket shows (login required)
  * Streamray:
    - Added models tags
    - Added Online / Offline switch on "Favorites" tab

5.2.3
========================
* Fix one directory per group
* Add Stripchat tags thx to @winkru
* Fix follow / unfollow for Stripchat thx to @winkru
* Fix: Loading the config failed with model URLs, which contained spaces
* Fix recording size not properly being reported and transferred between
  server and client

5.2.2
========================
* Fix: MyFreeCams model state updates
* Fix: app won't start, if cache is disabled
* Fix: excessive spawning of threads
* Fix: show notification and clear input field after adding a model

5.2.1
========================
* Fixed playlist parsing for Dreamcam
* Fixed adding models in the web interface
* Added help section to web interface (thx @Jafea for the idea)
* Added re-run post-processing to web interface (thx @Jafea)

5.2.0
========================
* Fix Chaturbate browsing
* Remove recommended tab from Chaturbate, since it is not working anymore
* Fix export / import of model notes and portraits when running in client 
  server mode
* Change recording size updates. The recording size is now tracked during the
  the recording process. Should reduce file system access drastically
* Added user defined browser. Code by @XxInvictus
* Upgrade to Java 21.0.1
* Update to JavaFX 21.0.1
* Changes by @WinkRU
  * added Dreamcams, Streamray and WinkTV
  * decrease requests to the Chaturbate main domain by using a clever online
    detection mechanism
  * Added "New" tab for XLoveCam
  * Fixed StripChat recordings, plus much less requests to site
  * Banned or deactivated StripChat models are automatically moved to the 
    "Later" list. The "Check URL" button removes them completely.
  * Fixed online check for Flirt4Free. If there are spaces in the model name,
    it might not work correctly
  * Added some integration with CamGirlFinder.net
    CamGirlFinder will help you find your favorite models on other cam sites.
    You can use ctbrec context menu to search by preview image or by model name
  * Stripchat: New tab "Girls VR" and support for recording VR streams
    (disabled by default, check settings).
  * Amateur.tv: Added model search to UI
  * BongaCams: Fixed model search, authorization not required now.
  * Flirt4Free: Fixed "New Girls" tab. Now it shows all new models, not just
    random one or two.
  * New option in settings: "Check for new versions on startup".
    Enabled by default.
  * LiveJasmin: Fixed navigation on models preview tabs. Now you can see all of
    them, not only first page.
  * "Recordings" tab: Added new column: "Site"
  * "Recording" tab: Fixed a bug, which caused the model list take a long time
    to load
  * Implemented file cache for thumbnails. This will reduce network traffic and
    (sometimes) speed up tabs. The maximum cache size can be changed in the 
    settings.
  * Streamray: Added "Favorites" tab
  * Stripchat: Added "Mobile" tab
  * Cam4: Added alternative method to get stream URL if standard doesn't work
  * MVLive, Showup: Fixed tabs navigation. Same models were displayed on all 
    pages.
  * Amateur.TV: Fixed navigation. After last site changes same models were
    displayed on all tabs.
  * Cam4: Some streams may have been recorded without an audio. This version
    will fix it (probably).
  * New recordings directory structure: "One directory for each group". 
    If model not in any group, it work as "One for each model"
  * Stripchat: Added "Online"/"Offline" switch on "Followed" tab.
  * Streamray: Added "Follow"/"Unfollow" in context menu.
  * Chaturbate: Fixed login on "Followed" tab.

5.1.2
========================
* !! See also the changes in 5.1.1, if you update from 5.1.0
* Fix unwanted delay between recordings if the recording is split by time or
  size
* Implement file system monitoring to monitor the size of recordings
* Fix bug in search popup, which caused the results to be glitched
* Fix bug in Streamate causing many requests to the search
* Upgrade to JavaFX 20.0.1
* Upgrade to Java Adoptium 17.0.7

5.1.1
========================
* Fixed StripChat recordings, thanks to @WinkRU for figuring it out
* Fixed bug, which caused all model user names with their display name.
  This is a bad one and probably the cause, that many models are not being
  detected as online or that the recordings don't start.
  If possible, delete the 5.1.0 configuration and let ctbrec migrate an
  old configuration again.
  Alternatively you can delete the models and re-add them to fix their
  user names, but this will reset the timestamps in the columns "last seen",
  "last recorded" and "added at"

5.1.0
========================
* Partially fixed LiveJasmin recordings. They only worked, if manually started
  from the GUI and you have to have an account configured. Otherwise the
  recording will stop after about two and a half minutes
* Changed recorder logic to prevent stalled recordings
* Model notes are now stored on the server
* Model potraits are now stored on the server
* The path to the recording metadata file is now adjusted after the config
  has been copied 

5.0.3
========================
* Fixed MV Live
* Reduced the amount of filesystem reads for recordings
* Fixed bugs in the migration of Chatubate model names to lower case 
  If you lost model portraits or model notes, revert your config to a
  known good backup (probably 4.7.17) and delete or move all newer configs.
  Then start 5.0.3
* Added a few more tabs for Chaturbate

5.0.2
========================
* Fix Stripchat recordings

5.0.1
========================
* Fix ConcurrentModificationException during conversion of Chaturbate model
  names to lower case

5.0.0
========================
* Fixed recording of original quality for Stripchat
* Add option to start ctbrec minimized. You might want to use the no-splash
  starters, so that the splash screen does not pop up on start
* Convert Chaturbate model names to lower case
* Added checkbox to disable post-processors
* Changed post-processing variable processing.
  !!! This change is not compatible with previous versions. You must change /
  adjust your configuration !!!
  See the help section for details.

4.7.17
========================
* Fixed Cam4 pages
* Fixed tray icon bug where the GUI would not open again, if it was minimized
  by clicking in the tray icon
* Ignored models will not be added to the recorder

4.7.16
========================
* Fixed Bongacams online check

4.7.15
========================
* Fixed bug in new config backup mechanism, which throws an error on systems
  where ctbrec has not been run before

4.7.14
========================
* Fixed bug with model groups. The check, if another model from the same
  group is already or could be recorded used an potentially outdated model
  object from the persisted groups.json file. Now the model state is updated
  before performing the check.
* Fixed: File handles not released for failed segments
* Changed config backup mechanism: Instead of creating a backup of the config
  in case of an error, ctbrec now creates a backup of the config on each start.
  Up to 5 backups are kept, older backups will be deleted automatically
  The backup will be created right next to the current config dir. E.g.:
  ```
  ctbrec
    |- 4.7.13
    |- 4.7.13_backup_2022-11-12_18-03-35_712
  ```

4.7.13
========================
* Added "Trans" tab for Cam4
* Added login through minimal browser for Chaturbate
* Token label is now loaded on tab selection and not on creation of the GUI

4.7.12
========================
* Fixed BongaCams
* Fixed MVLive
* Fixed loading of ShowupTV thumbnails
* Upgrade to Java 17
* Upgrade to JavaFX 19
* Known Problems:
  * Chaturbate login does not work
  * Chaturbate is much more aggressive with the 429 errors. It seems they also
    have switched on the Cloudfare browser check
  * Cherry TV login does not work

4.7.11
========================
* Fix Stripchat thumbnails

4.7.10
========================
* Fix Cam4 stackvaults stream recordings
* Add a volume setting for sound notifications
* HLS recordings can now be streamed continuously while the recording is running

4.7.9
========================
* Fix Flirt4Free recordings
* Fix stream resolution detection for Camsoda
* Fix bug in settings where panels would be empty
* Fix bug in MyFreeCams online detection
* Update JavaFX to 18.0.1

4.7.8
========================
* Fix Stripchat recordings

4.7.7
========================
* Fix cherry.tv overview pages

4.7.6
========================
* Save config in a sub-directory for each version.
* Fix minimize to tray
* Add setting to disable tab dragging, because that might be the cause for tab 
  freezes
* Fix Stripchat recordings
* Fix Stripchat thumbsnails
* Fix MVLive tabs

4.7.5
========================
* Add setting to show the number of active recordings in the tray
* Add a timeout of 2 seconds for each model check to make sure the online
  check doesn't get blocked somehow
* Increased log level for the online check
* Increased max heap size to 1GiB
* Ignore recordings without actual video data instead of deleting the metadata

4.7.4
========================
* Fixed AmateurTV recordings 

4.7.3
========================
* Fixed loading of config / MVLiveModels

4.7.2
========================
* Fixed Camsoda recordings (thx @Ban)
* Fixed Camsoda followed tab
* Fixed MVLive tab
* Fixed LiveJasmin Followed tab
* Fixed Cherry.TV overview pages
* Fixed thumbnails in Camsoda search results
* Updated minimal browser to electron 17.0.1

4.7.1
========================
* Fix: model groups not exported from server but from local config
* Fix: Threshold for segment errors didn't trigger restart of recording in 
  standalone mode (and when recording to single file)
* Add setting to customize the date time format in the GUI

4.7.0
========================
* This version requires Java 16
* Add import / export function for models
* Add setting to define a default duration for "record until"
* Improved online detection for MFC models
* Fixed detection of stalled HLS recordings
* Added threshold for segment errors. If a recording exceeds a certain amount
  of segment download errors per time period the recording is stopped. The idea
  behind that is, that a restarted recording gets connected to a different CDN
  server and has fewer errors (with Chaturbate in mind especially).
* Variables are now properly passed to the media player (in standalone mode)

4.6.1
========================
* Fixed adding of Streamate models
* Fixed Flirt4Free
* Updated bundled Java to 17.0.1

4.6.0
========================
* Added SecretFriends
* Added Cherry.tv
* Fixed Streamte
* Fixed Camsoda thumbnails
* Fixed LiveJasmin Search
* Added couples tab to LiveJasmin
* Added couples tab to Flirt4Free
* Added setting to enable a Recording tab per site
* Added a toggle to disable events

4.5.5
========================
* Fixed AmateurTV recordings
* Fixed a bug in stalled recording detection
* Added confirmation dialog  back in for model removal
* Fixed a bug in Showup recordings, which would restart a recording, if the 
  post-processing was restarted

4.5.4
========================
* Fix LiveJasmin followed tab
* Fix: two recordings starting for one model at the same time
* Fix: starting recordings from the "record later" tab did not work in client /
  server mode
* Added model notes to the recordings table
* Added resolution to the recordings table
* The server now writes the playlist on-the-fly based on the segment information
  from the original playlist. This allows to stream the recording while it is
  still running.
* Model placeholders can now be used for player params
  - ${modelName}
  - ${modelDisplayName}
  - ${modelSanitizedName}
  - ${modelNotes}
  - ${siteName}
  - ${siteSanitizedName}
* Add buttons to settings to delete cookies per site
* Fix bug in minimal browser

4.5.3
========================
* Fix Cam4 login
* Remove Camsoda shows tab
* Add setting to configure a timeout window when not record. In this timeframe
  no new recordings are started. Ongoing recordings will not be interrupted 
  though

4.5.2
========================
* Fix Flirt4Free recordings
* Added column "added at" to model tables
* Increased max priority value to 10,000
* Added setting to set a default priority
* Added support for absolute paths in the create contact sheet post-proc. step
* Change group settings only if the user clicks on OK
* Start recording only if the user clicked on OK in the record until dialog
* Added "mark for later" as additional action in the record until dialog
* Bugfix: Tabs locking up, if an error occurs

4.5.1
========================
* Fixed perfromance problem in recorded models tabs (I think :))
* Main tabs can now be rearranged
* Added setting to change the font
* Added setting to hide table grid lines
* Fixed thumbs in LiveJasmin followed tab

4.5.0
========================
* Added portrait column to Recording tab. The image to show can be selected in
  the context menu. This feature is a client-side only feature.
* Added button to configure, which columns should be shown on the Recording tab
* Added data transfer detection to HLS downloads, so that downloads don't
  get stuck in recording state. Recordings will stop now, if now segment was
  downloaded for 30 seconds.
* Fix: record until clock not showing up in recorded models tab
  in client / server mode
* Improved account existance check for chaturbate
* Improved account existance check for bongacams

4.4.5
========================
* Fixed Stripchat recordings
* Fixed ConcurrentModificationException, which caused the recorded models tab
  to turn blank in client / server mode

4.4.4
========================
* Fixed Camsoda token label
* Removed Camsoda Shows Tab
* Added Chaturbate configuration parameter to throttle requests to avoid 
  429 errors. Be aware that this also slows down the online check for Chaturbate
  models, especially, if you have a lot of models in your list.
  You have to play around a bit to find a value, which works for you.
* Fixed ConcurrentModificationException, which caused the recorded models tab
  to turn blank
* Fixed recordings not stopping, if playlist requests returned 403 or 404
* LiveJasmin recordings now first check the high res stream and fall back to 
  the low res stream, if it is not available
* Added data transfer detection to ShowupWebrtcDownload, so that downloads don't
  get stuck in recording state

4.4.3
========================
* Changed Camsoda audio codec back to AAC. Sound should be back for recordings
* Unified all model related context menus

4.4.2
========================
* Fixed memory leak caused by minimizing to tray
* Fixed Camsoda online check

4.4.1
========================
* Fixed Camsoda stream URLs

4.4.0
========================
* Added Amateur.TV
* Added XloveCam
* Improved Chaturbate search
* Fixed problem with MFC segment downloads by restricting MFC to HTTP/1.1
* Fixed tipping function
* Fixed bug in recording precondition check, which caused recordings to get 
  restarted. The bug occured when model groups were used in combination with
  priorities.

4.3.1
========================
* Fixed bug in server communication. The server always returned HTTP 400,
  because of an inverted "if-condition".
* Fixed bug in the post-processing variable replacement. The error occurs,
  if you use a variable, which value resolves to nothing (null)

4.3.0
========================
* Added mechanism to group models. This mechanism can also be used to define
  a model alias. Just create a new group with only one model
* Added new post-processing variables modelGroupName and modelGroupId
* Added possibility to define a default value for post-processing variables
  For example: ``${modelGroupName?${modelSanitizedName}}``
* Added time to "stop recording at"

4.2.1
========================
* Fixed Showup.tv downloads using the websocket stream instead of HLS
* Fixed bug, which caused the window to stay invisible after being minimized to
  tray on windows

4.2.0
========================
* App can now be minimized to tray
* Fixed unfollow for Cam4 models

4.1.3
========================
* Fixed Stripchat pagination bug
* Fix bug, which causes the deletion of the ignored models list

4.1.2
========================
* Fixed bug, which caused some recordings to get stuck
* Fixed follow/unfollow for CamSoda
* Fixed MVLive downloads
* Fixed bug in cookie handling, which also prevent MVLive downloads from working
* Ignore list is now saved as URLs only. The old format is not compatible
  anymore, so make sure, that you export them again, if you created a backup
  before.

4.1.1
========================
* Added open in browser to context menu of thumb overviews
* Fixed timestamp parsing bug in playlist parser
  (Should fix recording problems with Camsoda and Stripchat)
* Fixed thumbnails for BongaCams
* Removed some donation options. Thanks PayPal, for nothing. You suck!

4.1.0
========================
* Added dark mode for the server web interface (improvements on the CSS
  are welcome, I hate fiddling with CSS)
* Fixed Camsoda. They changed the construction of the playlist URLs again
* Remove "Bad selector" warning for Bongacams

4.0.0
========================
* Rewrite of the recorder internals
* Creation of contact sheets is much faster
* You can now add timestamps to the contact sheet
* Fix online state detection for Bongacams

3.13.1
========================
* Fixed Streamate tabs
* Fixed MVLive recordings

3.13.0
========================
* Added "Recently watched" tab. Can be disabled in Settings -> General
* Recording size now takes all associated files into account
* Removed restriction of download thread pool size (was 100 before)

3.12.2
========================
* Fix: Some Cam4 URLs were broken
* Fix: Cam4 search didn't work
* Stop hlsdl if the recording size didn't change for 90 seconds

3.12.1
========================
* Fix: "Resume all" started the recordings of models marked for later recording
* Fix: Login dialogs don't open
* Use 16:9 thumbnail format for MFC

3.12.0
========================
* Added "record later" tab to "bookmark" models
* Added config option to show the total number of models in the title bar
* Added support for hlsdl. Some sites (MV Live, LiveJasmin, Showup) are 
  excluded, because they need some special behavior while the download is
  running. hlsdl can be activated in the settings under "Advanced" or with
  the config properties "useHlsdl", "hlsdlExecutable" and "loghlsdlOutput".
  The used bandwidth calculation does not work with hlsdl.
* Fixed problem with Cam4 playlist URLs, thanks @gohufrapoc

3.11.0
========================
* Added config option for faster scroll speed
* Added a few more settings to the web interface
* Added config option to show confirmation dialogs for irreversible actions
* Disabled right click in context menus
* Fixed unjustified chaturbate follow / unfollow error dialog
* Use lowercase model names for Cam4. This should resolve recording problems
* Updated Configration.md page in help section
* Updated bundled Java to version 15.0.1
* Improved robustness of live previews (still experimental though)
* Some smaller UI tweaks here and there

3.10.10
========================
* Fixed MVLive recordings once again
* Fixed MVLive models being detected as online while being offline
* Fix: "Check URLs" button stays inactive after the first run
* Fix: recordings for some Cam4 models still didn't start
* Added "space used" to recordings tab
* Added menu item to add models in paused state to the "Recording" tab
* Added server setting to choose between fast and accurate playlist generation
* Some smaller tweaks here and there

3.10.9
========================
* Added more category tabs for CamSoda
* Added button to the "Recording" tab to go over all model URLs and check, if
  the account still exists
* Fix: some Cam4 models were not detected as online

3.10.8
========================
* Fixed Stripchat recordings. For some models the recording didn't start,
  even if they were online and publicly visible in the browser
* Fixed Bongacams "New" tab. It didn't show new models.
* Added setting to switch FFmpeg logging on/off (category Advanced/Devtools)

3.10.7
========================
* Fixed streaming of recordings from the server (the file path was duplicated
  if single file was used)
* Fixed credentials related bugs for Streamate and Stripchat.
  They used the user name from Chaturbate for some requests. Whoopsie!
* Renamed settings for Chaturbate's user name and password
* Added setting to split recordings by size
* Added setting to monitor the clipboard for model URLs and automatically add
  them to the recorder
* Fixed moving of segment recordings on the server (post-processing)
* Fixed minimal browser on macOS
* Minimal browser config is now stored in ctbrec's config directory

3.10.6
========================
* Fixed Cam4 downloads

3.10.5
========================
* Fixed MV Live downloads
* MFC web socket now uses the TLS URL
* Fix: date placeholders with patterns with more than one occurrence are
  replaced with the value of the first one
* Some smaller UI tweaks
    * adjusted component sizes for small resolutions
    * recording indicator can now be used to pause / resume the recording
    * adjusted scroll speed in the thumbnail overviews
    * added shortcuts for the thumbnail overviews (keys 1-9 and arrow keys)
    * added "stop" and "pause" to Recordings tab
    * added "follow" to Recordings tab

3.10.4
========================
* Fix: Bongacams login
* Fix: Minimal browser would freeze on windows
* Update minimal browser to Electron 10.1.5

3.10.3
========================
* Fix: Recordings couldn't be found in client server setup, if the client was
  running on Windows and the server on Linux
* Fix: Video length detection was done on the original file instead of the 
  post-processed one
* Added scrollbars to the settings tab to support smaller screens
* Added auto-redirect to the web-interface
* Added button to pause recording entirely without pausing all models

3.10.2
========================
* Fix: Flirt4Free browsing

3.10.1
========================
* Recordings now start immediately after resuming
* Improved Bongacams online state detection
* Fix: Stripchat models with @ in their name were not recorded
* Fix: Camsoda browsing, the "New" tab is gone though. The information
  is not available anymore
* You can now use variable to define the contactsheet file name

3.10.0
========================
* New post-processing
* Added support for thumbnails with different aspect ratios than 4:3
* Fix: MV Live models with spaces in the name not indicated as recording
* Fix: MV Live recordings stop every few minutes, if recorded with server
* Fix: Kind of fixed Showup.tv recordings. It does record now, but the
  recordings stop after a couple of minutes, because they now require you
  to be logged in. This has to be addressed in a future release
* Fix: Bongacams online check
* Fix: Bongacams unfollow model
* Fix: Streamate Followed tab
* Flirt4Free thumbnails are now actual previews instead of the bio pictures
  (thx @ward)
* Streamate thumbnails are now actual previews instead of the bio pictures
  (thx @WinkRU)
* Removed setting to delete too short recordings. This is now a post-process
  step, which has to be added in the settings
* Removed setting to remove a recording after post-processing. This is now
  a post-process step, which has to be added in the settings

3.9.0
========================
* Added support for Manyvids Live.
  Things that work:
  * Recording streams. Even more than one (this was a problem first, because
    they allow only one stream per session)
  * Search
  Things that don't work:
  * login / favorites
  * tipping
  * media player isn't working because of their authetication mechanism
* Fixed bug in recorder servlet. Actions for unpin and notes were mixed up
  and not properly synchronized between the server and the client
* Recordings now start immediately for newly added models
* Added confirmation dialog for "Pause All", "Resume All" and shutdown
* Fix: recording started event was not fired in client / server mode
* CTB Recorder now stops recording, if less than 100 MiB space is left
* New event, which is fired, if the disk is full (or less than the configured
  threshold is available)
* Fixed: MFC models changing to other models (I think, I found the problem.
  Can't be sure 100%)

3.8.6
========================
* Added setting to disable the online check for paused models
* Speed up shutdown process by stopping all recordings simultaneously
* Fixed Streamate followed tab once again
* Fixed: Flirt4Free models loose their name after some time
* Made loading of config file more robust for Flirt4Free models
* Added tab which shows the log output

3.8.5
========================
* Fixed Stripchat followed tab. It didn't work, if you have many favorited
  models
* Fixed: Some Stripchat models didn't get recorded
* Fixed: Some LiveJasmin models didn't get recorded
* Added support for temporary recordings. On the recording tab you can now set
  a date, when to stop recording a model and what to do afterwards 
  (pause or remove the model)
* Changed the look of the model table in the web interface a bit

3.8.4
========================
* Added support for xHamsterLive (go to Settings -> Sites -> Stripchat,
  switch to xHamsterLive, enter your credentials and restart)
* Fixed follow / unfollow for Stripchat
* Enable rerun PP for multiple recordings
* Fixed bug, which prevented recordings to finish properly on app
  shutdown. Recordings now shouldn't end up in state waiting anymore

3.8.3
========================
* Fixed Streamate
* Fixed favorites tab for Cam4; kind of, because only the online tab works.
  I currently don't see a way to retrieve the offline favorites
* Fixed favorites tab for CamSoda
* Fixed CamSoda recordings
* Added external login dialog for Stripchat to support the captcha

3.8.2
========================
* Fixed misconfiguration in global connection pool, which caused a lot of
  threads to spawn while browsing in the thumbnail overviews
* Improved memory handling for the thumbnail overviews. Thumbnail images were
  not released, when a tab was switched. This caused a huge memory consumption,
  if you opened a lot of different tabs.
* Fixed a bug in MFC websocket client, which caused to spawn a bunch of 
  "keep-alive" threads, if there was a problem with the connection
* Reworked the settings tab
* Fire recording finished event, if a download from the server is finished
* Ignore min/max resolution, if the resolution is unknown

3.8.1
========================
* Fixed recent MFC error
* Added log file rotation
* Fixed a bug with the resolution slider

3.8.0
========================
* Server Settings are now editable in the web-interface
* Models can be added by name in the web-interface
* Added a bandwidth monitor
* Added possibility to add notes to recordings
* Added range slider to restrict the recording resolution in a range; e.g. 480p - 1080p
* Improved MFC SD downloads (much less blocking, I think) 

3.7.3
========================
* Fixed problem, that MFC wouldn't show any models anymore

3.7.2
========================
* Fixed Chaturbate Login
* Added "New" tab to each site where it was missing
* Reverted change: Clear selection after deleting a recording

3.7.1
========================
* Server now logs in on startup, if credentials are set
* Show confirmation dialog on shutdown, if the are active downloads from the
  server
* Added setting to remove recordings after post-processing
* Added max resolution setting for the player (click on the gear!)
* Added systemd service example for the server
* Server now returns the version in the HTML and HTTP headers
* Improved server download progress calculation

3.7.0
========================
* Fixed the problem, that media players won't start anymore
* Fixed Stripchat login and favorites
* Added basic support for Showup.tv
  This version supports only recording, there is no support for:
  - stream resolution detection
  - login
  - favorites
  - search
  - tipping

3.6.4
========================
* Fixed race condition causing orphaned FFmpeg processes
  The problem was, that an error occured before FFmpeg was completely
  launched. ctbrec called internalStop, but the FFmpeg fields still
  pointed to null. ctbrec then finished the recording. In the meantime
  FFmpeg fired up and was abandoned by the recording.

3.6.3
========================
* Reactivated "Rerun post-processing" for the standalone version
* Fixed regression in last release. Only a few players would start
* Fixed possible error in code for merged downloads

3.6.2
========================
* Fixed regression in FFmpeg recording code introduced by last update
  Recording MP4 now works again
* Fixed bug in player launcher, which prevented recordings starting
  with '-' from starting, because it was interpreted as command line option
* Fixed minor bug in the actions panel in the settings tab
* Updates in help section 

3.6.1
========================
* Fix Streamate
* Removed outdated settings for MFC
* Fix JSON parsing bug for MFC
* Added button to jump to the first page
* Improve handling of recording termination. Hopefully this will fix the
  problem with left over FFmpeg processes 

3.6.0
========================
* Fixed MFC downloads (fingers crossed)
* Added Girls HD tab for Stripchat
* Fix follow/unfollow for BongaCams
* Save column order in tables
* Increase models per page for Streamate favorites
* Take model description into account when filtering

3.5.0
========================
* Filter terms can now be negated by prepending them with a "!"
* Added pinning for recordings. Pinned recordings cannot be deleted
* Added possibility to specify media player parameters
* Added config setting for the number of post-processing threads
* Added config setting for the HTTP User-Agent header
* Improved caching of stream resolution information

3.4.0
========================
* Added support for Stripchat
* Fixed login browser popups on Windows

3.3.0
========================
!! Caution: There is a new flag in the recordings meta-data. To be safe make
a backup of your recordings or move them to a different directory.

* Re-implement direct downloads to a single file.
  The download of the segments is still done by ctbrec. But the merging is done
  by FFmpeg. The merging is now done on-the-fly without downloading the 
  segments first.
  There are new settings for the recorder with which you can define the file
  format for recordings. Default is still MPEG transport stream, because it
  works the best.
* Enabled the server to record to a single file. Set the following variables in
  the server.json:

    "ffmpegFileSuffix": "mp4",
    "ffmpegMergedDownloadArgs": "-c:v copy -c:a copy -movflags faststart -y -f mp4",
    "recordSingleFile": true,

3.2.1
========================
* Fixed LiveJasmin HD recordings
* Fixed LiveJasmin followed tab

3.2.0
========================
* Fixed Streamate
* Added jump by letter key for tables on the Recording and Recordings tabs
* Added "Ignore" to the context menu of the Recording tab
* Fix: High CPU load by the MFC websocket
* Marked MyFreeCams as broken (but left it in)

3.1.0
========================
* Added recording priorities for models. If you restrict the number of 
  concurrent downloads, models with high priority will be favored over models 
  with low prio. Running recordings of models with low prio might even get
  stopped, so that models with higher prio can get recorded. Models with
  higher priority will also get checked first in the online check loop.
  You can adjust the prio on the "Recording" tab by double-clicking on the
  value or by using your scroll wheel while holding down CTRL.
* Added columns "last recorded" and "last seen" to models table
* Added menu entry to open the recording dir of a model

3.0.4
========================
* MFC now uses DASH again :) You can switch betwenn DASH and HLS in the settings
  for MFC
* The stream quality selection dialog now contains the entry "Best", which lets
  you switch back to the default setting
* The online state of models is now checked in parallel for the different 
  camsites.
* Fix: possible OutOfMemoryError because of too large thumbnail images
* Add possibility to export and import the ignore list
* Add manual refresh to context menu of thumbnail overviews
* New start script for the linux server (can possibly also be used on macOS)

3.0.3
========================
* MFC now uses HLS again
* Fix: In some cases a lot of recordings have been created, because they
  failed immediately after start
* Fix: Recorded models now don't switch their positions in the thumb overview
* HLS downloads now try to update the segment playlist URL, if the playlist cannot
  be loaded.
* DASH downloads stop faster, if the manifest cannot be loaded, because the model
  went offline
* The output from FFmpeg is now stored in merge.log in the segments directory of 
  a recording
* Fix: Possible deadlock in recorder

3.0.2
========================
* Fix: HLS downloads now create a temporary directory (ending with .part)
  similar to DASH downloads. This should fix video corruption and the problem
  of accidental file deletions
* Fix: CamSoda recordings notworking for some models. This was caused by
  new stream URLs, which are used for some, but not all models
* Retry to download DASH playlist 10 times before finally giving up
* Improved error handling for Cam4
* Improved error handling in the recorder code  

3.0.1
========================
* Fix: "Delete recordings shorter than" deleted all HLS recordings
* Fix: Post-Processing scripts now run on DASH and HLS downloads
  !! Attention !! You might have to check you PP-scripts and adjust them
* Change condition, if the PP context menu is shown or not
* Improved DASH download behaviour:
  - if the loaded init segments are empty ctbrec now retries to download them
  - if segment downloads fail, ctbrec retries 10 times with an increasing
    amount of time in between the reties; this has decreased the number of
    missing segments drastically

3.0.0
========================
* Reenabled MFC
* Add support for MFC DASH streams
* Both HLS and DASH downloads use FFmpeg to merge segments to MP4 files
* Fix: Flirt4Free overviews didn't work anymore
* Fix: Favorites page for Streamate


2.2.0
========================
* Added HMAC authentication support to the webinterface
* Added support for SSL/TLS
* Added support to change the context path of the server. This is helpful, if
  you want to run ctbrec behind a proxy. E.g. Apache or NGINX


2.1.0
========================
This release is mainly for the server.

* Added webinterface for the server. Has to be activated in the server config
  (webinterface). You can access it via http://host:port/static/index.html
* Disabled MyFreeCams for the time being. (If you are brave, you can still 
  use an older version, but don't blame me, if your account or IP is getting 
  blocked)
* Fix: Corrupt config files prevented the app from starting
* A few smaller fixes


2.0.1
========================
* Fix: ctbrec freezes on shutdown
* Fix: download and playback in client / server mode, if recordingsDir ends
  with a /
* Fix: Flirt4Free thumb overviews and recording


2.0.0
========================
* Complete rewrite of the recording code
* Added split recordings for the server
* Added menu entry to rerun the post-processing script
* Fix: CamSoda overview
* Fix: BongaCams model online check 
* Fix: Downloads not working in client/server setup (regression in last version)
* Fix: post-processing for split recordings
* Fix: All recordings are finished properly on shutdown (with playlist
  generation on the server and post-processing)

1.21.1
========================
* Added support for Flirt4Free
    * Live previews don't work
    * Some players might not be able to play the stream, because Flirt4Free 
      uses HLS AES encryption (it works with VLC)
    * Server recordings are played as singular segments and not one stream.
      Not sure why that happens. Probably something is off in the MPEG 
      transport stream

1.20.0
========================
* Fix: (This time for real, I think ;) ) Online status detection for BongaCams
* Fix: The login dialogs sometimes caused several error messages to pop up
* Added documentation: http://localhost:5689/docs/index.md
  Please contribute, if you think something is missing and could be explained
  here.
* Added notes column for recorded models
* Added filter for recorded models
* Added mechanism to ignore models. Ignored models will not show up in the
  thumbnail overviews anymore. This might be useful, if you like to browse for 
  new models to record, and want to hide models you don't like, so that they
  don't show up again in the future
* Multi-selection in the thumbnail overview with ctrl instead of shift
 
1.19.1
========================
* Fix: Online status detection for BongaCams
* Fix: Streamate search
* Added URL setting for BongaCams
* Fix: Memory leak in MFC client
* Fix: Previews showing up despite being disabled
* Updated bundled Java version to JDK 12
* Updated JavaFX version to 12

1.19.0
========================
* Added news tab, which shows my Mastodon timeline @ctbrec@mastodon.cloud
* Implemented follow/unfollow for BongaCams
* Added a limit setting for concurrent recordings
* Added menu entry to regenerate the playlist in case something went wrong
* Fixed: Playlist generator fails, if a segment's duration cannot be determined
* Added 5 min option for split recordings
* Improved server postprocessing
* Improved deletion of too short recordings for server-mode
* Use the model ID in the file name instead of the model name for FC2Live

1.18.0
========================
* Added FC2Live
* Fix #156 Multiple Windows 10 notification icons
* Implemented adding LiveJasmin models by URL
* Added active recording counter to the title (#155)
* Fix #141: Added seconds and milliseconds to recording timestamp
  !!! Caution !!! Existing recordings won't show up on the recordings
  tab unless you change the filename to match the new format

1.17.1
========================
* Improved LiveJasmin recordings. Login is not required anymore (thanks to M1h43ly)
  HD recordings should also work much better
* Added setting for the base URL for LiveJasmin
* Fixed CamSoda thumbnail overviews

1.17.0
========================
* Added LiveJasmin
  There are some issues, though:
    * live previews don't work
    * it's best to have an account and to be logged in, otherwise you might get
      errors after some time
    * the pagination and sorting of the models is random, because the 
      pagination LiveJasmin uses is quite obscure
* Added an electron based external browser component, which makes logins, which are 
  secured by Google's recaptcha, more reliable. This should also fix the login problems 
  with BongaCams (#58)
* Added a docker file for the server (thanks to bounty1342)
* Fixed Streamate favorites tab
* Added a setting for the thumbnail overview update interval

1.16.0
========================
* Thumbnails can show a live preview. Can be switched on in the settings.
* Live preview is experimental for now, because I noticed some funky behavior
  of the the internal media player. You can use it on your own risk.
* Added Streamate (metcams, xhamstercams, pornhublive)
* Maximum resolution can be an arbitrary value now
* Added setting for minimal recording length. Recordings, which are shorter
  than this value, get deleted automatically.
* Double-click in Recording tab starts the player
* Fix: BongaCams friends tab not working
* Fix: BongaCams search fails with JSON exception
* Fix: In some cases MFC models got confused

1.15.0
========================
* Fix: BongaCams overview didn't work anymore
* Fix: CamSoda overview didn't work anymore
* Fix: Multi selection of thumbnails didn't work when a tab was opened the
  first time
* Fix: Cam4 online detection was to restrictive
* Added tabular view for MFC, which shows all online models

1.14.0
========================
* Added setting for MFC to ignore the upscaled (960p) stream
* Added event system. You can define to show a notification, play a sound or
  execute a program, when the state of a model or recording changes
* Added "follow" menu entry on the Recording tab
* Fix: Recordings change from suspended to recording by their own when a
  thumbnail tab is opened and the model is showing
* Fix: Linux scripts don't work on systems where bash isn't the default shell
* Improved loading and display of resolution tags. They are not re-loaded
  everytime you switch between tabs

1.13.0
========================
* Added  possibility to open small live previews of online models
  in the Recording tab
* Added setting to toggle "Player Starting" message
* Added possibility to add models by their URL
* Added pause / resume all buttons
* Setting to define the base URL for MFC and CTB
* The paused checkbox are now clickable
* Implemented multi-selection for Recording and Recordings tab
* Fix: Don't throw exceptions for unknown attributes in PlaylistParser
* Fix: Don't do space check, if minimum is set to 0
* Fix: Player not starting when path contains spaces

1.12.1
========================
* Fixed downloads in client / server mode

1.12.0
========================
* Added threshold setting to keep free space on the recording device.
  This is useful, if you don't want to use up all of your storage.
  The free space is also shown on the recordings tab
* Tweaked the download internals a lot. Downloads should not hang
  in RECORDING state without actually recording. Downloads should
  be more robust in general.
* Fixed and improved split recordings
* Improved detection of online state for Cam4 models
* Accelerated the initial loading of the "Recording" tab for many
  Chaturbate models
* Recordings tab now shows smaller size units (Bytes, KiB, MiB, GiB) 

1.11.0
========================
* Added model search function
* Added color settings to change the appearance of the application
* Added setting for the online check interval
* Added setting to define the tab the application opens on start
* Double-click starts playback of recordings
* Refresh of thumbnails can be disabled
* Changed settings are saved immediately (including changes of the
  list of recorded models)

1.10.0
========================
* Fix: HMAC authentication didn't work for playing and downloading of a
  recording
* Fix: MyFreeCams model names were case sensitive
* Text input on "Recording"-tab now does auto completion for the site name
* Added menu entry to open the directory of a recording
* Post-processing script is now run outside ot the recordings directory
  Make sure, you use absolute paths
* Added setting to configure the directory structure for recordings
* Split up client and server into separat packages. The server package
  only depends on Java 1.8 and can be run with the 32-bit JRE, too.

1.9.0
========================
* Dropped support for Windows 32 bit
* Include JavaFX, so that ctbrec works with OpenJRE and Java >= 11
* Updated embedded Java versions to 11.0.1
* Added column "Recording" to recorded models tab, which indicates that
  a recording is currently running
* Fix: BongaCams recordings didn't start
* Fix: Unfollow for Cam4 didn't work
* Fix: Post-Processing script couldn't be removed
* A lot of smaller changes under the hood

1.8.0
========================
* Added BongaCams
* Added possibility to suspend the recording for a model. The model stays in
  the list of recorded models, but the actual recording is suspended
* HTTP sessions are restored on startup. This should reduce the number of 
  logins needed (especially for Cam4, BongaCams and CamSoda).
* Server can run now run on OpenJRE
* Added JVM parameter to define the configuration directory
  (``-Dctbrec.config.dir``)
* Improved memory management for MyFreeCams

1.7.0
========================
* Added CamSoda
* Added detection of model name changes for MyFreeCams
* Added setting to define a maximum resolution
* Fixed sorting by date in recordings table

1.6.1
========================
* Fixed UI freeze, which occured for a high number of recorded models
* Added Cam4
* Updated the embedded JRE for the Windows bundles to 8u192

1.6.0
========================
* Added support for multiple cam sites
* Sites can be switched on and off in the settings
* Added MyFreeCams
* Fixed proxy authentication for HTTP and SOCKS

1.5.4
========================
* Lots of little tweaks for the GUI

1.5.3
========================
* Recording time is now converted to local timezone and formatted nicely
* The state is now displayed in the resolution tag, if the room is not
  public (e.g. private, group, offline, away)
* You can now filter for public rooms with the keyword "public", if
  the display of resolution is enabled
* Added possibility to switch between online and offline models in the
  followed tab
* Added possibility to send tips

1.5.2
========================
* Added possibility to select multiple models in the overview tabs by
  holding SHIFT while clicking
* Added possibility to stop a recording in the recordings tab
* The delete key can now be used in the recorded models tab and in the 
  followed tab to unfollow one ore more models

1.5.1
========================
* Added setting to split up the recording after x minutes 
* Fixed possible OutOfMemoryError, which was caused by invalid transport
  stream packets
* Fixed possible OutOfMemoryError, which could occur, if the stream was
  downloaded faster than it could be written to the hard drive

1.5.0
========================
* Recordings are now stored in a single file
* The server still saves segments, downloads are
  one single file, too
* Recordings and downloads are now proper transport streams
  (continuity and timestamps get fixed, if invalid)

1.4.3
========================
* Added possibility to switch the video resolution for a recording
* Added selection box below the overview pages to change the thumbnail size
* Save and restore window size, location and maximized state
* Added check for OpenJDK and JavaFX on start to print out a better error,
  if JavaFX is not available

1.4.2
========================
* Enabled proxy authentication for SOCKS4 and HTTP
* Empty recording directories are now ignored instead of cluttering the log 
  file with error messages

1.4.1
========================
* Added proxy settings
* Made playlist generator more robust
* Fixed some issues with the file merging
* Fixed memory leak caused by the model filter function
