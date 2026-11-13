function updateOnlineModels() {
    try {
        let action = '{"action": "listOnline"}';
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
        }).done(function(data, textStatus, jqXHR) {
            if (data.status === 'success') {
                onlineModels = data.models;
            } else {
                if (console)
                    console.log('request failed', data);
            }
            updateModels();
        }).fail(function(jqXHR, textStatus, errorThrown) {
            if (console)
                console.log(jqXHR, textStatus, errorThrown);
        });
    } catch (e) {
        if (console)
            console.log('Unexpected error', e);
    }
    setTimeout(updateOnlineModels, 3000);
}

function isModelInArray(array, model) {
    for ( let idx in array) {
        let m = array[idx];
        if (m.url === model.url) {
            return true;
        }
    }
    return false;
}

/**
 * Synchronizes models from the server with the displayed knockout model table
 */
function syncModels(models) {    
    // remove models from the observable array, which are not in the updated list
    var newModelsMap = new Map(models.map(m => [m.url, m]));
    observableModelsArray.remove(m => !newModelsMap.get(m.ko_url()));
    
    var modelsMap = new Map(observableModelsArray().map(m => [m.ko_url(), m]));
    
    for (let model of models) {
        const m = modelsMap.get(model.url);
        
        if (!m) {
            // add models to the observable array, which are new in the updated list
            model.ko_name = ko.observable(model.name);
            model.ko_url = ko.observable(model.url);
            model.ko_online = ko.observable(false);
            for ( let i in onlineModels) {
                let onlineModel = onlineModels[i];
                if (onlineModel.url === model.url) {
                    model.ko_online(true);
                    break;
                }
            }
            model.ko_recording = ko.observable(model.online && !model.suspended);
			//model.ko_recording_class = ko.observable( (model.online && !model.suspended) ? 'fa fa-circle red' : '' );
            model.ko_suspended = ko.observable(model.suspended);
            model.swallowEvents = false;
            model.ko_suspended.subscribe(function(checked) {
                if (model.swallowEvents) {
                    return;
                }
                if (!checked) {
                    ctbrec.resume(model);
                } else {
                    ctbrec.suspend(model);
                }
            });
            observableModelsArray.push(model);
        } else {
            // update existing models
            m.ko_name(model.name);
            m.ko_url(model.url);
            let onlineState = false;
            for ( let i in onlineModels) {
                let onlineModel = onlineModels[i];
                if (onlineModel.url === model.url) {
                    onlineState = true;
                    break;
                }
            }
            m.ko_online(onlineState);
            //m.ko_recording_class( (model.online && !model.suspended) ? 'fa fa-circle red' : '');
            m.swallowEvents = true;
            m.ko_suspended(model.suspended);
            m.swallowEvents = false;
            m.ko_recording(m.ko_online() && !m.ko_suspended());
        }
    }
}

function updateModels() {
    try {
        let action = '{"action": "list"}';
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
                syncModels(data.models);
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
}