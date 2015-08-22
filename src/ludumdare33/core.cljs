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

(def player-run-anim-delay 10)

(def player-frames
  [[[128 8] [128 40] [128 72] [128 104]]
   [[168 8] [168 40] [168 72] [168 104]]])

(def props
  {
   :tree1 [[72 120] [48 48]]
   :tree2 [[160 152] [64 104]]})

(def level
  [
   [:tree1 [-30 30]]
   [:tree1 [100 100]]
   [:tree1 [250 100]]
   [:tree2 [-150 -400]]
   [:tree2 [-100 34]]
   ])

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

      (go
        (macros/with-sprite canvas :world
          [spr (sprite/make-sprite (first running) :scale scale
                                   :xhandle 0.5 :yhandle 1.0)]

          (<! (resources/fadein spr :duration 0.5))

          (loop [n 0]
            (log spr)
            (sprite/set-texture! spr (nth running (mod n (count running))))
            (<! (events/wait-frames player-run-anim-delay))
            (recur (inc n)))

          (<! (events/wait-time 10000))
          (<! (resources/fadeout spr :duration 0.5))))


      (go (macros/with-sprite canvas :world
            [tree (sprite/make-sprite
                   (texture/sub-texture spritesheet [72 120] [48 48])
                   :x -80 :y 30 :scale scale
                   :xhandle 0.5 :yhandle 1.0)]
            (<! (events/wait-time 10000))))

      (macros/with-sprite canvas :world
        [tree (sprite/make-sprite
               (texture/sub-texture spritesheet [160 152] [64 104])
               :x 300 :y -200 :scale scale
               :xhandle 0.5 :yhandle 1.0)]
        (<! (events/wait-time 10000)))


      )

    ))




(defonce _ (main))
