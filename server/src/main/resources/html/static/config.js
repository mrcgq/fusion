function loadConfig() {
    try {
        $.ajax({
            type : 'GET',
            url : '../config',
            dataType : 'json',
            async : true,
            timeout : 60000,
            headers : {
                'CTBREC-HMAC' : CryptoJS.HmacSHA256('', hmac)
            }
        }).done(function(data, textStatus, jqXHR) {
            if (textStatus === 'success') {
                while (observableSettingsArray().length > 0) {
                    observableSettingsArray.pop();
                }
                for (let i = 0; i < data.length; i++) {
                    let param = data[i];
                    if (param.type !== 'STRING_LIST') {
                      // could not get ko.observable() to write to param.value
                      // TODO: either fix that, or we need to take ko_value in saveConfig() instead
                      param.ko_value = ko.pureComputed({
                          read: function () {
                              return this.value;
                          },
                          write: function (value) {
                              this.value = value
                          },
                          owner: param
                      });
                    } else {
                      param.ko_value = ko.pureComputed({
                          read: function () {
                              return this.value.join('\n');
                          },
                          write: function (value) {
                              this.value = value.split('\n')
                          },
                          owner: param
                      });
                    }
                    observableSettingsArray.push(param);
                }
            } else {
                if (console)
                    console.log('request failed', data);
            }
        }).fail(function(jqXHR, textStatus, errorThrown) {
            if (console)
                console.log(jqXHR, textStatus, errorThrown);
        });
    } catch (e) {
        if (console)
            console.log('Unexpected error', e);
    }
}

function saveConfig() {
    try {
        let msg = JSON.stringify(observableSettingsArray());
        $.ajax({
            type : 'POST',
            url : '../config',
            dataType : 'json',
            async : true,
            timeout : 60000,
            headers : {
                'CTBREC-HMAC' : CryptoJS.HmacSHA256(msg, hmac)
            },
            data : msg
        }).done(function(data, textStatus, jqXHR) {
            if (textStatus === 'success') {
                loadConfig();
                $.notify('Configuration saved. You might have to restart the server.', 'info');
            } else {
                if (console)
                    console.log('request failed', data);
                $.notify('Error: ' + jqXHR.responseText, 'error');
            }
        }).fail(function(jqXHR, textStatus, errorThrown) {
            if (console)
                console.log(jqXHR, textStatus, errorThrown);
            $.notify(errorThrown + ': ' + jqXHR.responseText, 'error');
        });
    } catch (e) {
        if (console)
            console.log('Unexpected error', e);
        $.notify(errorThrown + ': ' + jqXHR.responseText, 'error');
    }
}