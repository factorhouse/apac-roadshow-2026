const Websocket = (function () {

    let connected = false;
    let worker;

    let connect = function () {
        if (!connected) {
            worker = new Worker("scripts/websocket_worker.js?name=" + Chat.getUsername());
            //messages received from worker thread
            worker.onmessage = function(event) {
                const processingEvent = event.data;

                switch (processingEvent.eventType) {
                    case "WEBSOCKET_CONNECTED":
                        connected = true;
                        console.log("Connected to the web socket");
                        Chat.updateUI();
                        break;
                    case "CHAT_MESSAGE":
                        Chat.handleMessage(processingEvent.payload, processingEvent.toUserId);
                        break;
                    case "RANDOM_NUMBER_POINT":
                        LineGraph.appendDatapoint(processingEvent.payload);
                        break;
                    case "TIMESERIES":
                        LineGraph.resetSeries(processingEvent.payload);
                        break;
                    case "ONLINE_USERS":
                        Chat.handleOnlineUsers(processingEvent.payload);
                        break;
                    case "GEO_COORDINATE":
                        Geolocation.addEarthquake(
                            processingEvent.payload.longitude,
                            processingEvent.payload.latitude
                        );
                        break;
                    default:
                        console.error("Unsupported inter-worker event: " + processingEvent);
                }
            };

            document.addEventListener("visibilitychange", () => {
                worker.postMessage({eventType: "VISIBILITY_CHANGE", payload: {pageIsVisible: !document.hidden}});
            });
        }
    };

    let isConnected = function () {
        return connected;
    };

    let postMessage = function (message) {
        worker.postMessage(message);
    };

    return {
        isConnected,
        connect,
        postMessage
    };

})();
