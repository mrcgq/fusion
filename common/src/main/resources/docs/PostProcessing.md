#### Post-Processing

The post-processing gives you the possibility to execute different actions after a recording has finished. You can use that to convert
the files to another format, create preview images, rename / move the file etc.

##### Available Steps

- **create a copy** - Creates a copy of the original recording. All following post-processing steps are executed on the copy, not on the
  original recording. This means, that the post-processing can be rerun in case a step failed, because the original recording is still
  available.
- **rename** - Renames the recording with the help of variables. See [Variables](#variables)
- **move** - Moves the recording with the help of variables. See [Variables](#variables)
- **remux / transcode** - Executes FFmpeg with the given arguments on the recording. This step can be used to convert a recording to
  MP4 or re-encode it to a different format / resolution. The input and output file don't have to be defined. If the step was successful
  the input file gets deleted.
- **execute script** - Executes a script or program with the given arguments. You can use the [variables](#variables) to define what
  to pass over to the script.
- **delete too short** - Delete a recording, if it is shorter than the given duration. This post-processing step has replaced the
  setting, which was available in the post-processing category before
- **delete original** - This is a companion step for "create a copy". If you use the copy step and all other steps are successful,
  you probably want to get rid of the original file and just keep the result of the post-processing. That's what this step does.
- **remove recording, but keep the files** - Removes the recording from the recordings list, but keeps the files untouched.
- **create contactsheet** - create a contact sheet with preview images of the recording

#### Planned for future releases

- **call a webhook** - call a URL once a recording is finished
- **create timeline thumbnails** - create a small thumbnail for every second or every few seconds, which can be used to very fast
  scan through a recording

#### How to configure the server to do post-processing

There is currently no user interface to configure the post-processing for the server. It has to be added manually to the server config.
I suggest to start the app and configure the post-processing steps in the settings. Afterwards you close the app and copy the
post-processing section from the settings.json to your server.json file. To find out, where these files are on your system, read
[Configuration File](ConfigurationFile.md).

The part you have to copy is

```
  postProcessors: [
    ...
  ],
```

#### Variables

<a id="variables" />

###### Available variables:

- **${modelName}** - the name of the recorded model
- **${modelDisplayName}** - the name of the recorded model, which is shown on the webpage. Might be the same as
  ${modelName}
- **${modelGroupName}** - name of the model group, if the model is part of a group
- **${modelGroupId}** - the unique ID of the model group, if the model is part of a group
- **${siteName}** - the name of the cam site, the model streams on
- **${fileSuffix}** - the file extension of the recording. E.g. ts or mp4. In case of a standard server recording,
  this will be empty
- **${epochSecond}** - timestamp of the recording in seconds since 1970-01-01 (unixtime)
- **${modelNotes}** - sanitized model notes. The following characters are replaced by an underscore:
  \\, /, ', " and space
- **${recordingNotes}** - sanitized recording notes. The following characters are replaced by an underscore:
  \\, /, ', " and space. Useful for the download of recordings from the server.
- **${recordingsDir}** - the base directory of all recordings. Same as Recordings Directory in the Recorder settings
  section.
- **${absolutePath}** - the absolute path in the filesystem to the recording file (or the recording directory in case of
  a server recording)
- **${absoluteParentPath}** - the absolute path to the parent directory of the recording in the filesystem (or the
  recording dir in case of a server recording)
- **${utcDateTime}** and **${localDateTime}** - the timestamp of the recording in the UTC or your local timezone

#### Functions

- **$trim** - removes all leading and trailing space - `$trim(  hello world  )` becomes `hello world`
- **$upper** - converts all of the characters to upper case - `$upper(hello world)` becomes `HELLO WORLD`
- **$lower** - converts all of the characters to lower case - `$lower(hElLo WORLD)` becomes `hello world`
- **$capitalize** - capitalizes words changing the first character to upper case - `$capitalize(hElLo WorLD)` becomes `HElLo WorLD`
- **$sanitize** - removes problematic characters - `$sanitize(hEl'Lo / WO"RLD)` becomes `hEl_Lo___WO_RLD`. The following characters are replaced by an
  underscore:
  \\, /, ', " and space
- **$orElse** - provide an alternative in case a variable is not set - `$orElse(${variable},someValue)` - becomes `${variable}`, if it is set or `someValue`, if
  `${variable}` is not set
- **$format** - formats a date, can be used with a pattern or without.
  Examples:
    - `$format(${localDateTime})` - becomes something like `2023-02-26_12-23-15`
    - `$format(${localDateTime},yyyyMMdd-HHmmss)` would lead to `20200928-173605`
  <table class="table-striped">

 <thead>
  <tr><th scope="col">Symbol</th>   <th scope="col">Meaning</th>         <th scope="col">Presentation</th> <th scope="col">Examples</th>
 </tr></thead>
 <tbody>
   <tr><th scope="row">G</th>       <td>era</td>                         <td>text</td>              <td>AD; Anno Domini; A</td>
   </tr><tr><th scope="row">u</th>       <td>year</td>                        <td>year</td>              <td>2004; 04</td>
   </tr><tr><th scope="row">y</th>       <td>year-of-era</td>                 <td>year</td>              <td>2004; 04</td>
   </tr><tr><th scope="row">D</th>       <td>day-of-year</td>                 <td>number</td>            <td>189</td>
   </tr><tr><th scope="row">M/L</th>     <td>month-of-year</td>               <td>number/text</td>       <td>7; 07; Jul; July; J</td>
   </tr><tr><th scope="row">d</th>       <td>day-of-month</td>                <td>number</td>            <td>10</td>
   </tr><tr><th scope="row">g</th>       <td>modified-julian-day</td>         <td>number</td>            <td>2451334</td>

   </tr><tr><th scope="row">Q/q</th>     <td>quarter-of-year</td>             <td>number/text</td>       <td>3; 03; Q3; 3rd quarter</td>
   </tr><tr><th scope="row">Y</th>       <td>week-based-year</td>             <td>year</td>              <td>1996; 96</td>
   </tr><tr><th scope="row">w</th>       <td>week-of-week-based-year</td>     <td>number</td>            <td>27</td>
   </tr><tr><th scope="row">W</th>       <td>week-of-month</td>               <td>number</td>            <td>4</td>
   </tr><tr><th scope="row">E</th>       <td>day-of-week</td>                 <td>text</td>              <td>Tue; Tuesday; T</td>
   </tr><tr><th scope="row">e/c</th>     <td>localized day-of-week</td>       <td>number/text</td>       <td>2; 02; Tue; Tuesday; T</td>
   </tr><tr><th scope="row">F</th>       <td>day-of-week-in-month</td>        <td>number</td>            <td>3</td>

   </tr><tr><th scope="row">a</th>       <td>am-pm-of-day</td>                <td>text</td>              <td>PM</td>
   </tr><tr><th scope="row">h</th>       <td>clock-hour-of-am-pm (1-12)</td>  <td>number</td>            <td>12</td>
   </tr><tr><th scope="row">K</th>       <td>hour-of-am-pm (0-11)</td>        <td>number</td>            <td>0</td>
   </tr><tr><th scope="row">k</th>       <td>clock-hour-of-day (1-24)</td>    <td>number</td>            <td>24</td>

   </tr><tr><th scope="row">H</th>       <td>hour-of-day (0-23)</td>          <td>number</td>            <td>0</td>
   </tr><tr><th scope="row">m</th>       <td>minute-of-hour</td>              <td>number</td>            <td>30</td>
   </tr><tr><th scope="row">s</th>       <td>second-of-minute</td>            <td>number</td>            <td>55</td>
   </tr><tr><th scope="row">S</th>       <td>fraction-of-second</td>          <td>fraction</td>          <td>978</td>
   </tr><tr><th scope="row">A</th>       <td>milli-of-day</td>                <td>number</td>            <td>1234</td>
   </tr><tr><th scope="row">n</th>       <td>nano-of-second</td>              <td>number</td>            <td>987654321</td>
   </tr><tr><th scope="row">N</th>       <td>nano-of-day</td>                 <td>number</td>            <td>1234000000</td>

   </tr><tr><th scope="row">V</th>       <td>time-zone ID</td>                <td>zone-id</td>           <td>America/Los_Angeles; Z; -08:30</td>
   </tr><tr><th scope="row">v</th>       <td>generic time-zone name</td>      <td>zone-name</td>         <td>Pacific Time; PT</td>
   </tr><tr><th scope="row">z</th>       <td>time-zone name</td>              <td>zone-name</td>         <td>Pacific Standard Time; PST</td>
   </tr><tr><th scope="row">O</th>       <td>localized zone-offset</td>       <td>offset-O</td>          <td>GMT+8; GMT+08:00; UTC-08:00</td>
   </tr><tr><th scope="row">X</th>       <td>zone-offset 'Z' for zero</td>    <td>offset-X</td>          <td>Z; -08; -0830; -08:30; -083015; -08:30:15</td>
   </tr><tr><th scope="row">x</th>       <td>zone-offset</td>                 <td>offset-x</td>          <td>+0000; -08; -0830; -08:30; -083015; -08:30:15</td>
   </tr><tr><th scope="row">Z</th>       <td>zone-offset</td>                 <td>offset-Z</td>          <td>+0000; -0800; -08:00</td>

   </tr><tr><th scope="row">p</th>       <td>pad next</td>                    <td>pad modifier</td>      <td>1</td>

   </tr><tr><th scope="row">'</th>       <td>escape for text</td>             <td>delimiter</td>         <td></td>
   </tr><tr><th scope="row">''</th>      <td>single quote</td>                <td>literal</td>           <td>'</td>
 </tr></tbody>
 </table>



For more information see: [DateTimeFormatter](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/format/DateTimeFormatter.html)

#### Full Example

`$orElse(${modelGroupName},$sanitize(${modelName}))_$format(${localDateTime})_${recordingNotes}`
