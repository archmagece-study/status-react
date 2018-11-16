(ns status-im.chat.subs
  (:require [clojure.string :as string]
            [re-frame.core :as re-frame]
            [status-im.chat.commands.core :as commands]
            [status-im.chat.commands.input :as commands.input]
            [status-im.chat.constants :as chat.constants]
            [status-im.chat.db :as chat.db]
            [status-im.contact.db :as contact.db]
            [status-im.models.transactions :as transactions]
            [status-im.utils.platform :as platform]))

(re-frame/reg-sub ::chats :chats)
(re-frame/reg-sub ::current-chat-id :current-chat-id)
(re-frame/reg-sub ::get-access-scope->command-id :access-scope->command-id)
(re-frame/reg-sub ::chat-ui-props :chat-ui-props)

(re-frame/reg-sub
 ::current-chat-ui-props
 :<- [::chat-ui-props]
 :<- [::current-chat-id]
 (fn [[chat-ui-props id]]
   (get chat-ui-props id)))

(re-frame/reg-sub
 ::current-chat
 :<- [::current-chat-id]
 :<- [::chats]
 (fn [[chat-id chats]]
   (get chats chat-id)))

(re-frame/reg-sub
 ::get-current-chat-messages
 :<- [:chats/active-chats]
 :<- [::current-chat-id]
 (fn [[active-chats chat-id]]
   (get-in active-chats [chat-id :messages])))

(re-frame/reg-sub
 ::get-current-chat-message-groups
 :<- [::current-chat]
 (fn [{:keys [message-groups]}]
   (or message-groups {})))

(re-frame/reg-sub
 ::get-current-chat-message-statuses
 :<- [::current-chat]
 (fn [{:keys [message-statuses]}]
   (or message-statuses {})))

(re-frame/reg-sub
 ::get-current-chat-referenced-messages
 :<- [::current-chat]
 (fn [{:keys [referenced-messages]}]
   (or referenced-messages {})))

(re-frame/reg-sub
 ::get-current-chat-messages-stream
 :<- [::get-current-chat-messages]
 :<- [::get-current-chat-message-groups]
 :<- [::get-current-chat-message-statuses]
 :<- [::get-current-chat-referenced-messages]
 :<- [:contacts/contacts]
 :<- [:account/account]
 (fn [[messages message-groups message-statuses referenced-messages contacts account]]
   (chat.db/get-current-chat-messages-stream messages message-groups message-statuses referenced-messages contacts account)))

(re-frame/reg-sub
 ::get-commands-for-chat
 :<- [:chat/id->command]
 :<- [::get-access-scope->command-id]
 :<- [:chats/current]
 (fn [[id->command access-scope->command-id chat]]
   (commands/chat-commands id->command access-scope->command-id chat)))

(re-frame/reg-sub
 ::show-suggestions-view?
 :<- [:chat/current-chat-ui-prop :show-suggestions?]
 :<- [:chats/current]
 :<- [:chat/all-available-commands]
 (fn [[show-suggestions? {:keys [input-text]} commands]]
   (and (or show-suggestions?
            (commands.input/starts-as-command? (string/trim (or input-text ""))))
        (seq commands))))

(re-frame/reg-sub
 ::show-suggestions?
 :<- [::show-suggestions-view?]
 :<- [:chat/selected-command]
 (fn [[show-suggestions-box? selected-command]]
   (and show-suggestions-box? (not selected-command))))

(re-frame/reg-sub
 ::cooldown-enabled?
 (fn [db]
   (:chat/cooldown-enabled? db)))

(re-frame/reg-sub :chat/id->command :id->command)

(re-frame/reg-sub
 :chat/contact
 :<- [:chats/current]
 (fn [chat]
   (:contact chat)))

(re-frame/reg-sub
 :chat/current-chat-ui-prop
 :<- [::current-chat-ui-props]
 (fn [ui-props [_ prop]]
   (get ui-props prop)))

(re-frame/reg-sub
 :chat/validation-messages
 :<- [::current-chat-ui-props]
 (fn [ui-props]
   (some-> ui-props :validation-messages)))

(re-frame/reg-sub
 :chat/input-margin
 :<- [:get :keyboard-height]
 (fn [kb-height]
   (cond
     (and platform/iphone-x? (> kb-height 0)) (- kb-height 34)
     platform/ios? kb-height
     :default 0)))

(re-frame/reg-sub
 :chats/active-chats
 :<- [::chats]
 :<- [:contacts/contacts]
 :<- [:contacts/blocked]
 :<- [:account/account]
 (fn [[chats contacts blocked-contacts {:keys [dev-mode?]}]]
   (chat.db/active-chats chats contacts blocked-contacts dev-mode?)))

(re-frame/reg-sub
 :chats/current
 :<- [:chats/active-chats]
 :<- [::current-chat-id]
 :<- [::get-current-chat-messages-stream]
 :<- [:contacts/contacts]
 (fn [[chats chat-id messages all-contacts]]
   (let [{:keys [contacts chat-id] :as current-chat} (get chats chat-id)
         public-key (or chat-id
                        (first contacts))]
     (cond-> (assoc current-chat :messages messages)
       public-key (assoc :contact (contact.db/public-key->contact contacts public-key))))))

(def ^:private map->sorted-seq (comp (partial map second) (partial sort-by first)))

(defn- available-commands [[commands {:keys [input-text]}]]
  (->> commands
       map->sorted-seq
       (filter (fn [{:keys [type]}]
                 (when (commands.input/starts-as-command? input-text)
                   (string/includes? (commands/command-name type) input-text))))))

(re-frame/reg-sub
 :chat/available-commands
 :<- [::get-commands-for-chat]
 :<- [:chats/current]
 available-commands)

(re-frame/reg-sub
 :chat/all-available-commands
 :<- [::get-commands-for-chat]
 (fn [commands]
   (map->sorted-seq commands)))

(re-frame/reg-sub
 :chat/selected-command
 :<- [:chats/current]
 :<- [:chat/current-chat-ui-prop :selection]
 :<- [::get-commands-for-chat]
 (fn [[{:keys [input-text]} selection commands]]
   (commands.input/selected-chat-command input-text selection commands)))

(re-frame/reg-sub
 :chat/input-placeholder
 :<- [:chats/current]
 :<- [:chat/selected-command]
 (fn [[{:keys [input-text]} {:keys [params current-param-position]}]]
   (when (string/ends-with? (or input-text "") chat.constants/spacing-char)
     (get-in params [current-param-position :placeholder]))))

(re-frame/reg-sub
 :chat/parameter-box
 :<- [:chats/current]
 :<- [:chat/selected-command]
 (fn [[_ {:keys [current-param-position params]}]]
   (when (and params current-param-position)
     (get-in params [current-param-position :suggestions]))))

(re-frame/reg-sub
 :chat/show-parameter-box?
 :<- [:chat-parameter-box]
 :<- [::show-suggestions?]
 :<- [:chat/validation-messages]
 :<- [:chat/selected-command]
 (fn [[chat-parameter-box show-suggestions? validation-messages {:keys [command-completion]}]]
   (and chat-parameter-box
        (not validation-messages)
        (not show-suggestions?)
        (not (= :complete command-completion)))))

(re-frame/reg-sub
 :chats/unread-messages-number
 :<- [:chats/active-chats]
 (fn [chats]
   (apply + (map :unviewed-messages-count (vals chats)))))

(re-frame/reg-sub
 :chat-animations
 (fn [db [_ key type]]
   (let [chat-id (subscribe [:get-current-chat-id])]
     (get-in db [:animations :chats @chat-id key type]))))

(re-frame/reg-sub
 :chats/transaction-confirmed?
 (fn [db [_ tx-hash]]
   (-> (get-in db [:wallet :transactions tx-hash :confirmations] "0")
       (js/parseInt)
       (>= transactions/confirmations-count-threshold))))

(re-frame/reg-sub
 :chats/wallet-transaction-exists?
 (fn [db [_ tx-hash]]
   (not (nil? (get-in db [:wallet :transactions tx-hash])))))

(re-frame/reg-sub
 :chat/cooldown-enabled?
 :<- [:chats/current]
 :<- [::cooldown-enabled?]
 (fn [[{:keys [public?]} cooldown-enabled?]]
   (and public?
        cooldown-enabled?)))

(re-frame/reg-sub
 :chat/reply-message
 :<- [:chats/current]
 :<- [::get-current-chat-messages]
 :<- [:contacts/contacts]
 :<- [:account/account]
 (fn [[{:keys [metadata]} messages contacts account]]
   (when-let [message (get messages (:responding-to-message metadata))]
     (chat.db/add-response-metadata message contacts account))))
