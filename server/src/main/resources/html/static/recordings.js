function play(recording) {
    let src = recording.singleFile ? '/hls/' + recording.id : recording.playlist;
    let hmacOfPath = CryptoJS.HmacSHA256(src, hmac);
    src = '..' + src;
    if(console) console.log("Path", src, "HMAC", hmacOfPath);
    if (hmac.length > 0) {
        src += "?hmac=" + hmacOfPath;
    }

    if(console) console.log('Playing video from', src);

    if(recording.singleFile) {
        var video = document.getElementById('player');
        video.src = src;
        video.load();
        console.log(video);
        video.oncanplay = function() {
            video.height = window.innerHeight * 0.85;
            $('#player-window').css('display', 'block');
            video.play();               
        };
        video.onerror = function() {
            $.notify(video.error.message, 'error');
        };
    } else {                
        if (Hls.isSupported()) {
            var video = document.getElementById('player');
            var hls = new Hls();
            hls.attachMedia(video);
            hls.on(Hls.Events.MEDIA_ATTACHED, function () {
                if(console) console.log(src);
                hls.loadSource(src);
                hls.on(Hls.Events.MANIFEST_PARSED, function (event, data) {
                    if(console) {
                        console.log(data);
                        console.log("manifest loaded, found " + data.levels.length + " quality level");
                    }
                    $('#player-window').css('display', 'block');
                    video.play();
                });
                
            });
        } else {
            $.notify('Loading HLS video streaming support failed', 'error');
            if(console) console.log('HLS is not supported');
        }
    }
}

function download(recording) {
    let src = recording.singleFile ? '/hls/' + recording.id : recording.playlist;
    let hmacOfPath = CryptoJS.HmacSHA256(src, hmac);
    src = '..' + src;
    if(console) console.log("Path", src, "HMAC", hmacOfPath);
    if (hmac.length > 0) {
        src += "?hmac=" + hmacOfPath;
    }
    location.href = src;
}

function calculateSize(sizeInByte) {
    let size = sizeInByte;
    let unit = "Bytes";
    if(size > 1024.0 * 1024 * 1024) {
        size = size / 1024.0 / 1024 / 1024;
        unit = "GiB";
    } else if(size > 1024.0 * 1024) {
        size = size / 1024.0 / 1024;
        unit = "MiB";
    } else if(size > 1024.0) {
        size = size / 1024.0;
        unit = "KiB";
    }
    return size.toFixed(2) + ' ' + unit;
}

function isRecordingInArray(array, recording) {
    for ( let idx in array) {
        let r = array[idx];
        if (r.id === recording.id) {
            return true;
        }
    }
    return false;
}

/**
 * Synchronizes recordings from the server with the displayed knockout recordings table
 */
function syncRecordings(recordings) {
    // remove recordings from the observable array, which are not in the
    // updated list
    for ( let idx in observableRecordingsArray()) {
        let recording = observableRecordingsArray()[idx];
        if (!isRecordingInArray(recordings, recording)) {
            observableRecordingsArray.remove(recording);
        }
    }

    // add recordings to the observable array, which are new in the
    // updated list
    for ( let idx in recordings) {
        let recording = recordings[idx];
        if (!isRecordingInArray(observableRecordingsArray(), recording)) {
            recording.ko_date = ko.computed(function() {
                return new Date(recording.startDate).toLocaleString('default', {
                    'day' : '2-digit',
                    'month' : '2-digit',
                    'year' : 'numeric',
                    'hour' : '2-digit',
                    'minute' : '2-digit',
                    'second' : '2-digit'
                })
            });
            recording.ko_progressString = ko.observable(recording.progress === -1 ? '' : recording.progress);
            recording.ko_size = ko.observable(calculateSize(recording.sizeInByte));
            recording.ko_status = ko.observable(recording.status);
            if (recording.singleFile) {
                recording.playlist = '/hls/' + recording.id;
            } else {
                recording.playlist = '/hls/' + recording.id + '/playlist.m3u8';
            }
            observableRecordingsArray.push(recording);
        }
    }

    // update existing recordings
    const recordingsMap = new Map(observableRecordingsArray().map(r => [r.id, r]));
      
    for (let recording of recordings) {
        const r = recordingsMap.get(recording.id);
        if (r) {
            r.progress = recording.progress;
            r.sizeInByte = recording.sizeInByte;
            r.status = recording.status;
            r.startDate = recording.startDate;
            r.ko_progressString(recording.progress === -1 ? '' : (recording.progress + '%'));
            r.ko_size(calculateSize(recording.sizeInByte));
            r.ko_status(recording.status);
        }
    }
}

function updateRecordings() {
    try {
        let action = '{"action": "recordings"}';
        $.ajax({
            type : 'POST',
            url : '../rec',
            dataType : 'json',
            async : true,
            timeout : 60000,
            headers : {
                'CTBREC-HMAC' : CryptoJS.HmacSHA256(action, hmac)
            },
            data : action
        }).done(function(data) {
            if (data.status === 'success') {
                syncRecordings(data.recordings);
                updateDiskSpace();
            } else {
                if (console)
                    console.log('request failed', data);
            }
        }).fail(function(jqXHR, textStatus, errorThrown) {
            if (console)
                console.log(textStatus, errorThrown);
        });
    } catch (e) {
        if (console)
            console.log('Unexpected error', e);
    }
    setTimeout(updateRecordings, 3000);
}

function updateDiskSpace() {
    let action = '{"action": "space"}';
    $.ajax({
        type : 'POST',
        url : '../rec',
        dataType : 'json',
        async : true,
        timeout : 60000,
        headers : {
            'CTBREC-HMAC' : CryptoJS.HmacSHA256(action, hmac)
        },
        data : action
    }).done(function(data) {
        if (data.status === 'success') {
            space.total(data.spaceTotal);
            space.free(data.spaceFree);
            space.percent((data.spaceFree / data.spaceTotal * 100).toFixed(2));
            space.text(calculateSize(data.spaceFree) + ' / ' + calculateSize(data.spaceTotal));
            throughput.bytes(data.throughput);
            throughput.timeframe(data.throughputTimeframe);
            let bytesPerSecond = data.throughput / data.throughputTimeframe * 1000;
            throughput.text(calculateSize(bytesPerSecond) + '/s');
        } else {
            if (console)
                console.log('request failed', data);
        }
    }).fail(function(jqXHR, textStatus, errorThrown) {
        if (console)
            console.log(textStatus, errorThrown);
    });
}