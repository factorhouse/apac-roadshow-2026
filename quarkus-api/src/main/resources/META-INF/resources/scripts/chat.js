const Chat = (function () {

    // these values are also hardcoded in the index.html file.
    // Ideally should be declared only once for both js and html files
    const ID = {
        CONNECT: "#connect",
        NAME: "#name",
        SEND: "#send",
        MESSAGE: "#msg",
        CHAT: "#chat",
        ONLINE_USERS: "#online-users",
        RECIPIENT: "#recipient",
        PREFERRED_LANGUAGE: "#language-select",
    }

    // state
    let recipients;

    let init = function() {
        let name = $(ID.NAME);

        $(ID.CONNECT).click(Websocket.connect);
        $(ID.SEND).click(sendMessage);

        name.keypress(function (event) {
            if (event.keyCode == 13 || event.which == 13) {
                Websocket.connect();
            }
        });

        $(ID.MESSAGE).keypress(function (event) {
            if (event.keyCode == 13 || event.which == 13) {
                sendMessage();
            }
        });

        $(ID.CHAT).change(function () {
            scrollToBottom();
        });

        name.focus();

        $(ID.ONLINE_USERS).on("click", "button", function() {
            populateRecipients();
            let recipient = $(ID.RECIPIENT)
            if (getUsername() !== this.value) {
                recipient.val(this.value).trigger('input'); ;
            }
        });
        let recipient = $(ID.RECIPIENT);
        recipient.on("input", function () {
            $(ID.MESSAGE).focus();
        });
        recipient.click(populateRecipients);
    }

    let getUsername = function() {
        return $(ID.NAME).val();
    }

    let updateUI = function() {
        $(ID.SEND).attr("disabled", false);
        $(ID.CONNECT).attr("disabled", true);
        $(ID.NAME).attr("disabled", true);
        $(ID.MESSAGE).focus();

        preferredLanguageChanged();
    };

    let handleMessage = function(chatMessage, toUserId) {
        let chat = $(ID.CHAT);

        let translated = chatMessage.translated ? `<span class='translated'><i>[translated]</i></span> ` : "";

        switch (chatMessage.type) {
            case "USER_JOINED":
                if (chatMessage.userId !== getUsername()) {
                    chat.append(`<span class='system-message'>${chatMessage.userId} joined the chatroom.</span><br>`);
                } else {
                    chat.append(`<span class='message'></span><span class='system-user'>System</span> : Howdy <span class="user">${chatMessage.userId}</span>!.</span><br>`);
                }
                break;
            case "USER_LEFT":
                chat.append(`<span class='system-message'>${chatMessage.userId} left the chatroom.</span><br>`);
                break;
            case "CHAT_MESSAGE":
                if (toUserId == null) {
                    const sender = chatMessage.userId === getUsername() ? 'You' : chatMessage.userId;
                    chat.append(`<span class='user'>${sender}</span> ${translated}: <span class='message'>${chatMessage.message}</span><br>`);
                }
                else if (toUserId === getUsername()) {
                    chat.append(`<span class='user'>${chatMessage.userId}</span> <i>(DM)</i> ${translated}: <span class='private-message'>${chatMessage.message}</span><br>`);
                }
                else if (chatMessage.userId === getUsername()) {
                    chat.append(`<span class='user'>You</span> <i>(to ${toUserId})</i> ${translated}: <span class='private-message'>${chatMessage.message}</span><br>`);
                }
                break;
        }
        scrollToBottom();
    };

    let handleOnlineUsers = function(onlineUsers) {
        recipients = onlineUsers.users;
        $(ID.ONLINE_USERS).html("");
        onlineUsers.users.forEach(function(user) {
          $(ID.ONLINE_USERS).append(`<li title="${user}"><button class="list-group-item" value="${user}">${user}</button></li>`);
        });
    };

    let populateRecipients = function() {
        let recipientDOM = $(ID.RECIPIENT);
        let selectedValue = recipientDOM.val();

        recipientDOM.html("");
        recipientDOM.append(`<option value="">Everyone</option>`);
        recipients.forEach(function(r) {
            if (getUsername() != r) {
                recipientDOM.append(`<option value="${r}">${r}</option>`);
            }
        });

        if (recipientDOM.find(`option[value="${selectedValue}"]`).length) {
                recipientDOM.val(selectedValue);
        }
    };

    let sendMessage = function () {
        if (Websocket.isConnected()) {
            let msg = $(ID.MESSAGE);
            let recipient = $(ID.RECIPIENT);
            var value = msg.val();
            msg.val("");

            if (value == "") {
                return;
            }

            var recipientValue = recipient.val();
            var chatMessage = {
                type: "CHAT_MESSAGE",
                userId: getUsername(),
                message: value,
                toUserId: null,
                preferredLanguage: $(ID.PREFERRED_LANGUAGE).val(),
            };
            if (recipientValue != "") {
                chatMessage.toUserId = recipientValue
            }
            Websocket.postMessage({eventType: "CHAT_MESSAGE", payload: chatMessage});
        }
    };

    let preferredLanguageChanged = function () {
        if (Websocket.isConnected()) {
            var chatMessage = {
                type: "LANGUAGE_SET",
                userId: getUsername(),
                message: null,
                toUserId: null,
                preferredLanguage: $(ID.PREFERRED_LANGUAGE).val(),
            };

            Websocket.postMessage({eventType: "CHAT_MESSAGE", payload: chatMessage});
        }
    };

    let scrollToBottom = function () {
        let chat = $(ID.CHAT);
        chat.scrollTop(chat[0].scrollHeight);
    };

    return {
        init,
        updateUI,
        getUsername,
        handleMessage,
        handleOnlineUsers,
        preferredLanguageChanged,
    };
})();
