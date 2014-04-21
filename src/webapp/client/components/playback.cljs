(ns webapp.client.components.playback
  (:require
   [goog.net.cookies :as cookie]
   [om.core          :as om :include-macros true]
   [om.dom           :as dom :include-macros true]
   [cljs.core.async  :refer [put! chan <! pub timeout]]
   [om-sync.core     :as async]
   [clojure.data     :as data]
   [clojure.string   :as string]
   [ankha.core       :as ankha])

  (:use
   [webapp.framework.client.coreclient :only  [log remote]]
   [webapp.client.globals              :only  [app-state   playback-app-state
                                               playback-controls-state]]
   [webapp.client.components.views     :only  [main-view]]
   [webapp.client.components.forms     :only  [request-form]]
   )
  (:use-macros
   [webapp.framework.client.neo4j      :only  [neo4j]]
   )
  (:require-macros
   [cljs.core.async.macros :refer [go]]))









(def playbacktime (atom 0))

(defn playback-session [& {:keys
                           [session-id]}]
  (go (let [ll (<! (neo4j "
                          match (r:WebRecord) where
                          r.session_id={session_id}
                          return r order by r.seq_ord
                          "
                          {:session_id    session-id}
                          "r"))]

        (om/root
         main-view
         playback-app-state
         {:target (js/document.getElementById "playback_canvas")})


        ;(log (pr-str (first ll)))
        (doseq [item ll]
          (let [
                path      (cljs.reader/read-string (:path (into {} item )))
                content   (cljs.reader/read-string (:new_state (into {} item )))
                timestamp   (:timestamp (into {} item ))
                ]
            (log path)
            (log content)
            (log timestamp )
            (<! (timeout (-  timestamp @playbacktime)))
            (reset! playbacktime timestamp)
            (reset! playback-app-state  content)

            nil
            )
          )
        ))
  )





(defn replay-session [session-id]
  (go
 (let [ll  (<! (neo4j "match (n:WebSession) return n.session_id"
                      {} "n.session_id"))]

   (reset! playback-controls-state (assoc-in
                                    @playback-controls-state
                                    [:data :sessions]  (into [](take 5 ll))))
   (log ll)
   (om/root
    main-view
    playback-app-state
    {:target (js/document.getElementById "playback_canvas")})

   ))

  )




(defn playback-session-button-component [{:keys [ui data sessions]} owner]
  (reify

    om/IRender
    ;---------

    (render
     [this]
     (dom/div nil


              (dom/div
               #js {
                    :style      #js {:padding-top "40px"
                                     :background-color
                                     (if
                                       (get-in ui
                                               [:sessions data :highlighted])
                                       "lightgray"
                                       ""
                                       )
                                     }

                    :onClick    (fn [e]
                                  ( playback-session
                                    :session-id data))
                    :onMouseEnter
                    (fn[e]
                      (om/update!
                       ui
                       [:sessions data :highlighted] true )
                      )
                    :onMouseLeave
                    (fn[e]
                      (om/update!
                       ui
                       [:sessions data :highlighted] false )
                      )
                    }
               (str data)
               )
              ))))















(defn playback-controls-view [app owner]
  (reify

    om/IInitState
    ;------------

    (init-state [_]

                {
                   :delete            (chan)
                })

    om/IWillMount
    ;------------
    (will-mount [_]
                (let [delete (om/get-state owner :delete)]
                  (go (loop []
                        (let [contact (<! delete)]
                          (om/transact! app :contacts
                                        (fn [xs] (vec (remove #(= contact %) xs))))
                          (recur))))))

    om/IRenderState
    ;--------------

    (render-state
     [this state]
     (log (str "map="(mapv
                                      (fn [x]
                                        {
                                        :ui      (-> app :ui)
                                        :data    x
                                        }
                                        )
                                      (-> app :data :sessions))))

     (dom/div nil
              (dom/h2 nil "Playback web sessions")


              (apply dom/ul nil
                     (om/build-all  playback-session-button-component
                                    (mapv
                                     (fn [x]
                                       {
                                        :ui      (-> app :ui)
                                        :sessions   (-> app :data :sessions)
                                        :data    x
                                        }
                                       )
                                     (-> app :data :sessions)))
                     )


              ))))