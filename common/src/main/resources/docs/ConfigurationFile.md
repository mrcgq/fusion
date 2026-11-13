#### Configuration File

The configuration file stores all your settings and recorded models.

##### Location

ctbrec application:

- Windows: `C:\Users\{your user name}\AppData\Roaming\ctbrec\settings.json`
- Linux: `~/.config/ctbrec/settings.json`
- macOS: `/Users/{your user name}/Library/Preferences/ctbrec/settings.json`

server:

- Windows: `C:\Users\{your user name}\AppData\Roaming\ctbrec\server.json`
- Linux: `~/.config/ctbrec/server.json`
- macOS: `/Users/{your user name}/Library/Preferences/ctbrec/server.json`

##### Values

The application and the server share the same configuration file structure. That's why there are
values, which don't make sense in the server configuration and vice versa. These values are simply
ignored. This is a collection of the most interesting values:

- **chooseStreamQuality** (app only) - [`true`,`false`] Opens the stream resolution selection dialog, when you start recording a model.

- **concurrentRecordings** - [0 - 2147483647] Limits the number of concurrently running recordings. Once this number is reached, now more recordings are started
  until a recording is finished. 0 means unlimited.

- **determineResolution** (app only) - [`true`,`false`] Display the stream resolution on the thumbnails.

- **hlsdlExecutable** - Path to the hlsdl executable, which is used, if `useHlsdl` is set to true

- **httpPort** - [1 - 65536] The TCP port, the server listens on. In the server config, this will tell the server, which port to use. In the application this
  will set
  the port ctbrec tries to connect to, if it is run in remote mode.

- **httpServer** (app only) - The TCP host where the server is running. Has no effect, if ctbrec is run in local reocrding mode.

- **httpTimeout** - [1 - 2147483647] in milliseconds. In the server configuration this sets the idle timeout for connections to the server. In the app this sets
  the connect and read timeout for any HTTP connection.

- **httpUserAgent** - The user agent, which is used in the HTTP header, when ctbrec connects to a camsite. This is used to disguise, that it actually is a
  recording software :)

- **httpUserAgentMobile** - Same as *httpUserAgent*, but in same cases we have to pretend to be a mobile phone :)

- **key** - Key for the HMAC based authentication. If this is set in server config and contains values, the HMAC authentication is automatically active.

- **livePreviews** (app only) - Enables the live preview feature in the app.

- **logFFmpegOutput** - [`true`,`false`] The output from FFmpeg (from recordings or post-processing steps) will be logged in temporary files.

- **loghlsdlOutput** - [`true`,`false`] The output from hlsdl will be logged in temporary files. Only in effect, if `useHlsdl` is set to true

- **minimumResolution** - [1 - 2147483647]. Sets the minimum video height for a recording. ctbrec tries to find a stream quality, which is higher than or equal
  to this value. If the only provided stream quality is below this threshold, ctbrec won't record the stream.

- **maximumResolution** - [1 - 2147483647]. Sets the maximum video height for a recording. ctbrec tries to find a stream quality, which is lower than or equal
  to this value. If the only provided stream quality is above this threshold, ctbrec won't record the stream.

- **minimumLengthInSeconds** - **Deprecated. Add a post-processing step instead. See [Post-Processing](PostProcessing.md)** [0 - 2147483647]
  Automatically delete recordings, which are shorter than this amount of seconds. 0 disables this feature.

- **minimumSpaceLeftInBytes** - [0 - 9223372036854775807] The space in bytes ctbrec should conserve on the hard drive. 1 GiB = 1024 MiB = 1048576 KiB =
  1073741824 bytes

- **onlineCheckIntervalInSecs** - [1 - 2147483647] How often ctbrec checks, if a model is online. This is not a guaranteed interval: If you record many models,
  the online check for all models can take longer than this interval. A minute is a reasonable value, but you can go lower, if you don't want to miss a
  anything. But don't go too low, or you risk to do too many requests in a short amount of time and get banned by some sites.

- **onlineCheckSkipsPausedModels** - [`true`,`false`] Skip the online check for paused models. If you have many models in the recording list, this can reduce
  the delay when a recording starts after a model came online.

- **postProcessing** - **Deprecated. See [Post-Processing](PostProcessing.md)** Absolute path to a script, which is executed once a recording is
  finished.

- **recordingsDir** - Where ctbrec saves the recordings.

- **recordingsDirStructure** (server only) - [`FLAT`, `ONE_PER_MODEL`, `ONE_PER_RECORDING`] How recordings are stored in the file system. `FLAT` - all
  recordings in one directory; `ONE_PER_MODEL` - one directory per model; `ONE_PER_RECORDING` - each recordings ends up in its own directory. Change this only,
  if you have `recordSingleFile` set to `true`

- **recordSingleFile** (server only) - [`true`,`false`] - How recordings are stored in the file system. `true` means, each recording is saved in one large
  file. `false` means, ctbrec just downloads the stream segments.

- **splitStrategy** - [`DONT`, `TIME`, `SIZE`, `TIME_OR_SIZE`] Defines if and how to split recordings. Also see `splitRecordingsAfterSecs`
  and `splitRecordingsBiggerThanBytes`

- **splitRecordingsAfterSecs** - [0 - 2147483647] in seconds. Split recordings after this amount of seconds. The recordings are split up into several individual
  recordings,
  which have the defined length (roughly). Has to be activated with `splitStrategy`.

- **splitRecordingsBiggerThanBytes** - [0 - 9223372036854775807] in bytes. Split recordings, if the size on disk exceeds this value. The recordings are split up
  into several individual recordings,
  which have the defined size (roughly). Has to be activated with `splitStrategy`.

- **timeoutRecordingStartingAt** - [00:00 - 23:59] - Start of the recording timeout timeframe - No new recordings will be started in this period

- **timeoutRecordingEndingAt** - [00:00 - 23:59] - End of the recording timeout timeframe - No new recordings will be started in this period

- **useHlsdl** - [`true`,`false`] Use hlsdl to record the live streams. You also have to set `hlsdlExecutable`, if hlsdl is not globally available on your
  system. hlsdl won't be used for MV Live, LiveJasmin and Showup.

- **webinterface** (server only) - [`true`,`false`] Enables the webinterface for the server. You can access it with http://host:port/static/index.html Don't
  activate this on
  a machine, which can be accessed from the internet, because this is totally unprotected at the moment. 
