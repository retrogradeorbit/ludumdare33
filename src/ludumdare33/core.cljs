(ns ^:figwheel-always ludumdare33.core
    (:require
     [infinitelives.pixi.canvas :as canv]
     [infinitelives.pixi.resources :as resources]
     [infinitelives.pixi.sprite :as sprite]
     [infinitelives.utils.events :as events]
     [infinitelives.utils.console :refer [log]]
     [infinitelives.pixi.texture :as texture]
     [infinitelives.utils.math :as math]
     [cljs.core.async :refer [<!]])
    (:require-macros
     [cljs.core.async.macros :refer [go]]
     [ludumdare33.macros :as macros]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:text "Hello world!"}))


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)


(def scale [3 3])

(defonce canvas
  (canv/init
   {:expand true
    :engine :auto
    :layers [:ground :world :ui]
    :background 0x2ec13c
    }))

(defonce render-go-block (go (while true
               (<! (events/next-frame))
               ;(log "frame")
               ((:render-fn canvas)))))

(def player-run-anim-delay 7)

(def player-frames
  [[[128 8] [128 40] [128 72] [128 104]]
   [[168 8] [168 40] [168 72] [168 104]]
   [[208 8] [208 40] [208 72] [208 104]]])

(def game-props
  {
   :tree1 [[72 120] [48 48]]
   :tree2 [[160 152] [64 104]]
   :rock [[16 160] [8 8]]
   :rock2 [[24 160] [16 8]]
   :grass1 [[16 168] [8 8]]
   :grass2 [[24 168] [8 8]]
   :grass3 [[32 168] [8 8]]
   :grass4 [[40 168] [8 8]]
   :grass5 [[48 168] [8 8]]
   :grass6 [[56 168] [8 8]]
   })

#_ (def level
  [
   [:tree1 [-30 30]]
   [:tree1 [100 100]]
   [:tree1 [250 100]]
   [:tree2 [-150 -400]]
   [:tree2 [-100 34]]
   ])

(def abundance
  {
   :grass1 100
   :grass2 300
   :grass3 300
   :grass4 400
   :grass5 500
   :grass6 500

   :rock 200
   :rock2 20
   :tree1 10
   :tree2 50})

(def level
  (into []
        (apply concat (for [[prop num] abundance]
                   (take num (repeatedly (fn [] [prop [(math/rand-between -10000 10000)
                                                       (math/rand-between -1000 1000)]])))
                   ))))


(println "LEVEL:")
(println level)

(defn make-prop-texture-lookup [props]
  (let [spritesheet (resources/get-texture :sprites :nearest)]
    (into {}
          (for [[prop-name [pos size]] props]
            [prop-name
             (texture/sub-texture spritesheet pos size)]))))


(defn depth-compare [a b]
  (cond
    (< (.-position.y a) (.-position.y b)) -1
    (< (.-position.y b) (.-position.y a)) 1
    :default 0))

(defn main []
  (go
    (<! (resources/load-resources
         (-> canvas :layer :ui)
         ["img/sprites.png"]
         :full-colour 0x6ecb64          ;0x005217
         :highlight 0x6ecb64
         :lowlight 0x0f7823
         :empty-colour 0x2ec13c
         :debug-delay 0.2
         :width 400
         :height 32
         :fade-in 0.2
         :fade-out 0.5))

    (let [spritesheet (resources/get-texture :sprites :nearest)
          topleft (player-frames 0)
          running (map
                   #(texture/sub-texture spritesheet
                                         %
                                         [24 24])
                   topleft)]


      (go (let [props (make-prop-texture-lookup game-props)]
            (macros/with-sprite-set canvas :world
              [sprites (doall (for [[prop [x y]] level]
                                (sprite/make-sprite (prop props)
                                                    :x x :y y
                                                    :scale scale
                                                    :xhandle 0.5 :yhandle 1.0)))]
                                        ;(log "!" (last sprites))
              (macros/with-sprite canvas :world
                [player (sprite/make-sprite (first running) :scale scale
                                         :xhandle 0.5 :yhandle 0.9)]

                (<! (resources/fadein player :duration 0.5))

                #_ (loop [n 0]
                  (sprite/set-texture! spr (nth running (mod n (count running))))
                  (<! (events/wait-frames player-run-anim-delay))
                  (recur (inc n)))

                (loop [[x y] [0 0] n 0]
                  (sprite/set-pivot! (-> canvas :layer :world) (* 6 x) y)
                  (sprite/set-pos! player (* 6 x) y)
                  (sprite/set-texture! player (nth running (mod (int (* 0.18 n)) (count running))))

                  (.sort (.-children (-> canvas :layer :world)) depth-compare )
                  (<! (events/next-frame))
                  (recur [(dec x) y] (inc n))
                  )

                (<! (resources/fadeout player :duration 0.5)))







              (<! (events/wait-time 200000)))

            ))


      )

    ))




(defonce _ (main))
