(ns ^:figwheel-always ludumdare33.core
    (:require
     [infinitelives.pixi.canvas :as canv]
     [infinitelives.pixi.resources :as resources]
     [infinitelives.pixi.sprite :as sprite]
     [infinitelives.utils.events :as events]
     [infinitelives.utils.console :refer [log]]
     [infinitelives.pixi.texture :as texture]
     [infinitelives.utils.math :as math]
     [infinitelives.utils.vec2 :as vec2]
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

#_ (def player-frames
  [[[128 8] [128 32] [128 56] [128 80]]
   [[168 8] [168 40] [168 72] [168 104]]
   [[208 8] [208 40] [208 72] [208 104]]])

(def player-frames
  {
   :left [[128 8] [128 32] [128 56] [128 80]]
   :down-left [[160 8] [160 40] [160 72] [160 104]]
   :down [[192 8] [192 40] [192 72] [192 104]]
   :down-right [[224 8] [224 40] [224 72] [224 104]]
   :right [[256 8] [256 32] [256 56] [256 80]]
   :up-right [[288 8] [288 40] [288 72] [288 104]]
   :up [[320 8] [320 40] [320 72] [320 104]]
   :up-left [[352 8] [352 40] [352 72] [352 104]]

})

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
          player-tex (into {}
                           (for [[k v] player-frames]
                             [k (doall (map
                                        #(texture/sub-texture spritesheet
                                                              %
                                                              [24 24])
                                        v))]))
          ]


      (go (let [props (make-prop-texture-lookup game-props)]
            (macros/with-sprite-set canvas :world
              [sprites (doall (for [[prop [x y]] level]
                                (sprite/make-sprite (prop props)
                                                    :x x :y y
                                                    :scale scale
                                                    :xhandle 0.5 :yhandle 1.0)))]
                                        ;(log "!" (last sprites))
              (macros/with-sprite canvas :world
                [player (sprite/make-sprite (-> player-tex :left first) :scale scale
                                         :xhandle 0.5 :yhandle 0.9)]

                (<! (resources/fadein player :duration 0.5))

                #_ (loop [n 0]
                  (sprite/set-texture! spr (nth running (mod n (count running))))
                  (<! (events/wait-frames player-run-anim-delay))
                  (recur (inc n)))

                (loop [pos (vec2/zero)
                       vel (vec2/vec2 -6 0)
                       n 0]
                  (let [next-pos (vec2/add pos vel)
                        x (aget next-pos 0)
                        y (aget next-pos 1)]

                    (sprite/set-pivot! (-> canvas :layer :world)  x y)
                    (sprite/set-pos! player x y)



                    ;; set animation frame
                    ;
                    (let [
                          pi Math/PI
                          pi-on-4 (/ pi 4)
                          pi-on-8 (/ pi 8)
                          head (vec2/heading vel)
                          section
                          (int (/ (+ pi-on-8 head) pi-on-4))
                          frame
                          ([:right :down-right :down :down-left
                            :left :up-left :up :up-right :right]
                           section)


                          ]
                      (sprite/set-texture! player (nth
                                                   (-> player-tex frame)
                                                   (mod (int (* 0.02 n)) (-> player-tex frame count)))))

                    (.sort (.-children (-> canvas :layer :world)) depth-compare )
                    (<! (events/next-frame))
                    (recur next-pos

                           ;;cursor keys
                           (let [x (if (events/is-pressed? :left)
                                     -1 (if (events/is-pressed? :right)
                                          1
                                          0))
                                 y (if (events/is-pressed? :up)
                                     -1 (if (events/is-pressed? :down) 1 0))]
                             (log (vec2/vec2 x y))
                             (log (vec2/unit (vec2/vec2 x y)))
                             (vec2/truncate (vec2/add
                                             (if (and (= x 0) (= y 0))
                                               (vec2/vec2 x y)
                                               (vec2/scale (vec2/unit (vec2/vec2 x y))
                                                           1.5))
                                             (vec2/scale vel 0.98))
                                            10
                                            ))

                           (+ n (vec2/magnitude vel))))
                  )

                (<! (resources/fadeout player :duration 0.5)))







              (<! (events/wait-time 200000)))

            ))


      )

    ))




(defonce _ (main))
