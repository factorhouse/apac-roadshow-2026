const WebSocketWorker = (function () {

    const MAX_TIMESERIES_LENGTH = 300;
    const USERNAME = parseName(location.search);
    let timeseries = [];
    let pageIsVisible = true;
    let socket;

    function init() {
        socket = new WebSocket("ws://" + location.host + "/chat/" + USERNAME);
        socket.onopen = function () {
            self.postMessage({eventType: "WEBSOCKET_CONNECTED"});
        };
        socket.onmessage = handleSocketData;

        socket.onclose = function(e) {
            console.log('Socket is closed. Reconnect will be attempted in 1 second.', e.reason);
            setTimeout(function() {
                init();
            }, 1000);
        };
        socket.onerror = function(err) {
            console.error('Socket encountered error: ', err.message, 'Closing socket');
            socket.close();
        };
    }

    let handleSocketData = function (m) {
       const processingEvent = JSON.parse(m.data);

       switch (processingEvent.eventType) {
           case "CHAT_MESSAGE":
           case "ONLINE_USERS":
           case "GEO_COORDINATE":
               self.postMessage(processingEvent);
               break;
           case "RANDOM_NUMBER_POINT":
               handleDataPoint(processingEvent);
               break;
           default:
               console.error("Unsupported inter-process event: " + processingEvent);
       }
    };

    let handleMainThreadData = function(data) {
        switch (data.eventType) {
            case "CHAT_MESSAGE":
                socket.send(JSON.stringify(data.payload));
                break;
            case "VISIBILITY_CHANGE":
                handleVisibilityChange(data.payload);
                break;
            default:
                console.error("Unsupported inter-worker event: " + data);
        }
    };

    let handleVisibilityChange = function(visibilityChange) {
        pageIsVisible = visibilityChange.pageIsVisible;
        if (pageIsVisible) {
            resetTimeseries();
            self.postMessage({eventType: "TIMESERIES", payload: timeseries});
        }
    };

    let handleDataPoint = function(processingEvent) {
        let datapoint = processingEvent.payload;
        timeseries.push({x: datapoint.timestamp, y: datapoint.value});
        if (timeseries.length >= MAX_TIMESERIES_LENGTH) {
            resetTimeseries();
            if (pageIsVisible)
                self.postMessage({eventType: "TIMESERIES", payload: timeseries});
        }
        else if (pageIsVisible) {
            self.postMessage(processingEvent);
        }
    };

    function resetTimeseries() {
        timeseries.splice(0, timeseries.length - MAX_TIMESERIES_LENGTH/2);
    }

    function parseName(query) {
        return decodeURIComponent(query.split("=")[1]);
    }

    return {
        init,
        handleMainThreadData
    };

})();


WebSocketWorker.init();
self.onmessage = function(m) {
    WebSocketWorker.handleMainThreadData(m.data);
};
