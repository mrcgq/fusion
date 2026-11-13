#### Are ticket / gold / private shows supported?

No. I never intended to record these kind of shows and I never tested it. You might be able to record
them, but you have to figure it out yourself.

#### How can I convert the recordings to mp4 / mkv?

To convert the files to another format, you have to remux them. You don't have to reencode them, since the
files (usually, have not seen an exception yet) already contain H.264 video and AAC audio. You can remux the
files manually with tools like [Avidemux](Avidemux.md), [MKVToolNix](MKVToolNix.md) or
[FFmpeg](FFmpeg.md) or use one of the [post-processing](PostProcessing.md) scripts.

#### Streams are not getting recorded even though the model is online

- Is "Leave space on device set" and do you have enough space left?
- Is "Maximum resolution" set? In case maximum resolution is set and ctbrec cannot determine the
  resolution of a stream, the stream will not be recorded.
- Is "Concurrent Recordings" set and you reached the maximum?

#### How can I playback the recorded .ts files?

Use one of the following players:

- [mpv](https://mpv.io/installation/)
- [VLC](https://www.videolan.org/vlc/)

#### How can I playback the server recordings?

Use one of the following players:

- [mpv](https://mpv.io/installation/)
- [VLC](https://www.videolan.org/vlc/)

If you have direct access to the recordings you can use these players and open the playlist.m3u8.
Otherwise use the ctbrec client. Set the **Record Location** to **Remote** and configure your server.
Under **General** select the **Player** of your choice. Then you can start the playback directly from the
**Recordings** tab.

#### The login for site XYZ does not work anymore and the credentails work in a browser

Stop CTB Recorder. Then open the settings directory (check [ConfigurationFile](ConfigurationFile.md) for the location) and
delete the cookies file for that site. Start CTB Recorder again and it should work again. If it does not work, check the log
file for errors. The log file is called ctbrec.log and you can find it in the installation directory of CTB Recorder.
If that does not work go back to your settings directory, go up to the parent directory and delete ctbrec-minimal-browser, if it
exists.

#### It takes a long time until a recording starts for a model

You probably have a lot of models in the "Recording" list. CTB Recorder checks the models one after the other. This is done on
purpose to not fire too many requests in a short amount of time, because this can cause blocks by the camsites.

#### Can I run several instances of CTB Recorder

It is possible to define the configuration directory and configuration file, which is used by ctbrec. This way you
can create several instances with different configurations.
On Windows, create a file called `ctbrec.l4j.ini` right next to `ctbrec.exe`. Add one of the following settings
or both to the file:

To change the config directory:

`-Dctbrec.config.dir=C:\Users\boobface\Desktop\ctbrec\conf`

To change only the config file:

`-Dctbrec.config=abc.json`

On Linux and macOS edit `ctbrec.sh` and add one or both of the above mentioned settings to the start line (preferably directly after $JAVA):

`$JAVA -Dctbrec.config=alternate-settings.json -Djdk.gtk.version=3 -cp ctbrec-1.19.1-final.jar ctbrec.ui.Launcher`

<iframe width="560" height="315" src="https://www.youtube.com/embed/ATeS6HgADUo" frameborder="0" allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe>
