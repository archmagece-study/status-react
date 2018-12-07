(ns status-im.notifications.core
  (:require [goog.object :as object]
            [re-frame.core :as re-frame]
            [status-im.react-native.js-dependencies :as rn]
            [status-im.js-dependencies :as dependencies]
            [taoensso.timbre :as log]
            [status-im.i18n :as i18n]
            [status-im.accounts.db :as accounts.db]
            [status-im.chat.models :as chat-model]
            [status-im.utils.platform :as platform]
            [status-im.utils.fx :as fx]))

;; Work in progress namespace responsible for push notifications and interacting
;; with Firebase Cloud Messaging.

(when-not platform/desktop?

  (def firebase (object/get rn/react-native-firebase "default")))

;; NOTE: Only need to explicitly request permissions on iOS.
(defn request-permissions []
  (if platform/desktop?
    (re-frame/dispatch [:notifications.callback/request-notifications-permissions-granted {}])
    (-> (.requestPermission (.messaging firebase))
        (.then
         (fn [_]
           (log/debug "notifications-granted")
           (re-frame/dispatch [:notifications.callback/request-notifications-permissions-granted {}]))
         (fn [_]
           (log/debug "notifications-denied")
           (re-frame/dispatch [:notifications.callback/request-notifications-permissions-denied {}]))))))

(defn valid-notification-payload?
  [{:keys [from to] :as payload}]
  (and from to
       (or
        ;; full pubkey
        (and (= (.-length from) 132)
             (= (.-length to) 132))
        ;; anonymized pubkey hash (v2 payload)
        (and (= (.-length from) 10)
             (= (.-length to) 10)))))

(defn sha3 [s]
  (.sha3 dependencies/Web3.prototype s))

(defn anonymize-pubkey
  "Anonymize a public key by hashing it and taking the first 4 bytes"
  [pubkey]
  (if (= (.-length pubkey) 10)
    pubkey
    (apply str (take 10 (sha3 pubkey)))))

(defn create-notification-payload
  [{:keys [from to] :as payload}]
  (if (valid-notification-payload? payload)
    {:msg-v2 (js/JSON.stringify #js {:from (anonymize-pubkey from)
                                     :to   (anonymize-pubkey to)})}
    (throw (str "Invalid push notification payload" payload))))

(when platform/desktop?
  (defn handle-initial-push-notification [] ())) ;; no-op

(when-not platform/desktop?

  (def channel-id "status-im")
  (def channel-name "Status")
  (def sound-name "message.wav")
  (def group-id "im.status.ethereum.MESSAGE")
  (def icon "ic_stat_status_notification")

  (defn lookup-contact-pubkey-from-hash [{:keys [db] :as cofx} contact-pubkey-or-hash]
    (if (and contact-pubkey-or-hash
             (= (.-length contact-pubkey-or-hash) 10))
      (let [current-account-pubkey (get-in db [:account/account :public-key])]
        (if (= (anonymize-pubkey current-account-pubkey) contact-pubkey-or-hash)
          current-account-pubkey
          (if (accounts.db/logged-in? cofx)
            ;; TODO: for simplicity we're doing a linear lookup of the contacts, but we might want to build a map of hashed pubkeys to pubkeys for this purpose
            (:public-key (first
                          (filter #(= (anonymize-pubkey (:public-key %)) contact-pubkey-or-hash)
                                  (vals (:contacts/contacts db)))))
            (log/warn "failed to lookup contact from hash, not logged in"))))
      contact-pubkey-or-hash))

  (defn parse-notification-v1-payload [msg-json]
    (let [msg (js/JSON.parse msg-json)]
      {:version 1
       :from    (object/get msg "from")
       :to      (object/get msg "to")}))

  (defn parse-notification-v2-payload [msg-v2-json]
    (let [msg (js/JSON.parse msg-v2-json)]
      {:version 2
       :from    (object/get msg "from")
       :to      (object/get msg "to")}))

  (defn get-notification-payload [message-js]
    ;; message-js.-data is Notification.data():
    ;; https://github.com/invertase/react-native-firebase/blob/adcbeac3d11585dd63922ef178ff6fd886d5aa9b/src/modules/notifications/Notification.js#L79
    (let [data        (.. message-js -data)
          msg-v2-json (object/get data "msg-v2")]
      (try
        (let [payload (if msg-v2-json
                        (parse-notification-v2-payload msg-v2-json)
                        (parse-notification-v1-payload (object/get data "msg")))]
          (if (valid-notification-payload? payload)
            payload
            (log/warn "failed to retrieve notification payload from" (js/JSON.stringify data))))
        (catch :default e
          (log/debug "failed to parse" (js/JSON.stringify data) "exception:" e)))))

  (defn rehydrate-payload
    "Takes a payload with hashed pubkeys and returns a payload with the real (matched) pubkeys"
    [cofx {:keys [from to] :as payload}]
    (if (accounts.db/logged-in? cofx)
      {:version 1
       :from    (lookup-contact-pubkey-from-hash cofx from)
       :to      (lookup-contact-pubkey-from-hash cofx to)}
      payload))

  (defn display-notification [{:keys [title body from to]}]
    (let [notification (firebase.notifications.Notification.)]
      (.. notification
          (setTitle title)
          (setBody body)
          (setData (clj->js (create-notification-payload {:from from
                                                          :to to})))
          (setSound sound-name))
      (when platform/android?
        (.. notification
            (-android.setChannelId channel-id)
            (-android.setAutoCancel true)
            (-android.setPriority firebase.notifications.Android.Priority.High)
            (-android.setCategory firebase.notifications.Android.Category.Message)
            (-android.setGroup group-id)
            (-android.setSmallIcon icon)))
      (.. firebase
          notifications
          (displayNotification notification)
          (then #(log/debug "Display Notification" title body))
          (catch (fn [error]
                   (log/debug "Display Notification error" title body error))))))

  (defn get-fcm-token []
    (-> (.getToken (.messaging firebase))
        (.then (fn [x]
                 (log/debug "get-fcm-token:" x)
                 (re-frame/dispatch [:notifications.callback/get-fcm-token-success x])))))

  (defn create-notification-channel []
    (let [channel (firebase.notifications.Android.Channel. channel-id
                                                           channel-name
                                                           firebase.notifications.Android.Importance.High)]
      (.setSound channel sound-name)
      (.setShowBadge channel true)
      (.enableVibration channel true)
      (.. firebase
          notifications
          -android
          (createChannel channel)
          (then #(log/debug "Notification channel created:" channel-id)
                #(log/error "Notification channel creation error:" channel-id %)))))

  (fx/defn handle-on-message
    [cofx {:keys [from] :as decoded-payload}]
    (let [view-id (get-in cofx [:db :view-id])
          current-chat-id (get-in cofx [:db :current-chat-id])
          app-state (get-in cofx [:db :app-state])]
      (log/debug "handle-on-message" "app-state:" app-state "view-id:" view-id "current-chat-id:" current-chat-id "from:" from)
      (when-not (and (= app-state "active")
                     (= :chat view-id)
                     (= current-chat-id from))
        {:notifications/display-notification (merge {:title (i18n/label :notifications-new-message-title)
                                                     :body  (i18n/label :notifications-new-message-body)}
                                                    decoded-payload)})))

  (fx/defn handle-push-notification-open
    [{:keys [db] :as cofx} {:keys [stored?] :as decoded-payload}]
    (let [current-public-key (accounts.db/current-public-key cofx)
          nav-opts           (when stored? {:navigation-reset? true})
          rehydrated-payload (rehydrate-payload cofx decoded-payload)
          from               (:from rehydrated-payload)
          to                 (:to rehydrated-payload)]
      (log/debug "handle-push-notification-open" "current-public-key:" current-public-key "rehydrated-payload:" rehydrated-payload)
      (if (= to current-public-key)
        (fx/merge cofx
                  {:db (update db :push-notifications/stored dissoc to)}
                  (chat-model/navigate-to-chat from nav-opts))
        {:db (assoc-in db [:push-notifications/stored to] from)})))

  (defn handle-notification-open-event [event] ;; https://github.com/invertase/react-native-firebase/blob/adcbeac3d11585dd63922ef178ff6fd886d5aa9b/src/modules/notifications/Notification.js#L13
    (let [decoded-payload (get-notification-payload (.. event -notification))]
      (when decoded-payload
        (re-frame/dispatch [:notifications/notification-open-event-received decoded-payload]))))

  (defn handle-initial-push-notification
    "This method handles pending push notifications. It is only needed to handle PNs from legacy clients (which use firebase.notifications API)"
    []
    (.. firebase
        notifications
        getInitialNotification
        (then (fn [event]
                (when event
                  (handle-notification-open-event event))))))

  (defn setup-token-refresh-callback []
    (.onTokenRefresh (.messaging firebase)
                     (fn [x]
                       (log/debug "onTokenRefresh:" x)
                       (re-frame/dispatch [:notifications.callback/get-fcm-token-success x]))))

  (defn setup-on-notification-callback []
    "Calling onNotification is only needed so that we're able to receive PNs while in foreground"
    "from older clientswho are still relying on the notifications API."
    "Once that is no longer a consideration we can remove this method"
    (log/debug "calling onNotification")
    (.onNotification (.notifications firebase)
                     (fn [message-js]
                       (log/debug "handle-on-notification-callback called")
                       (let [decoded-payload (get-notification-payload message-js)]
                         (log/debug "handle-on-notification-callback payload:" decoded-payload)
                         (when decoded-payload
                           (re-frame/dispatch [:notifications.callback/on-message decoded-payload]))))))

  (defn setup-on-message-callback []
    (log/debug "calling onMessage")
    (.onMessage (.messaging firebase)
                (fn [message-js]
                  (log/debug "handle-on-message-callback called")
                  (let [decoded-payload (get-notification-payload message-js)]
                    (log/debug "handle-on-message-callback decoded-payload:" decoded-payload)
                    (when decoded-payload
                      (re-frame/dispatch [:notifications.callback/on-message decoded-payload]))))))

  (defn setup-on-notification-opened-callback []
    (.. firebase
        notifications
        (onNotificationOpened handle-notification-open-event)))

  (defn init []
    (setup-token-refresh-callback)
    (setup-on-message-callback)
    (setup-on-notification-callback)
    (setup-on-notification-opened-callback)
    (when platform/android?
      (create-notification-channel))))

(fx/defn process-stored-event [cofx address stored-pns]
  (when-not platform/desktop?
    (if (accounts.db/logged-in? cofx)
      (let [current-account (get-in cofx [:db :account/account])
            current-address (:address current-account)
            to              (:public-key current-account)
            from            (lookup-contact-pubkey-from-hash cofx (get stored-pns to))]
        (when (and from
                   (= address current-address))
          (log/debug "process-stored-event" "address" address "from" from "to" to)
          (handle-push-notification-open cofx
                                         {:version 1
                                          :from    from
                                          :to      to
                                          :stored? true})))
      (log/error "process-stored-event called without user being logged in!"))))

(re-frame/reg-fx
 :notifications/display-notification
 display-notification)

(re-frame/reg-fx
 :notifications/handle-initial-push-notification
 handle-initial-push-notification)

(re-frame/reg-fx
 :notifications/get-fcm-token
 (fn [_]
   (when platform/mobile?
     (get-fcm-token))))

(re-frame/reg-fx
 :notifications/request-notifications-permissions
 (fn [_]
   (request-permissions)))
