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
     [ludumdare33.sound :as sound]
     [ludumdare33.font :as font]
     [cljs.core.async :refer [<! chan >! close! timeout]])
    (:require-macros
     [cljs.core.async.macros :refer [go]]
     [ludumdare33.macros :as macros]))

(enable-console-print!)

(defonce fonts
  [(font/install-google-font-stylesheet! "http://fonts.googleapis.com/css?family=Amatic+SC")])


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
   :left [[128 8] [128 32] [128 56] [128 80] [128 104] [128 128]]
   :down-left [[160 8] [160 40] [160 72] [160 104] [160 136] [160 168]]
   :down [[192 8] [192 40] [192 72] [192 104] [192 136] [192 168]]
   :down-right [[224 8] [224 40] [224 72] [224 104] [224 136] [224 168]]
   :right [[256 8] [256 32] [256 56] [256 80] [256 104] [256 128]]
   :up-right [[288 8] [288 40] [288 72] [288 104] [288 136] [288 168]]
   :up [[320 8] [320 40] [320 72] [320 104] [320 136] [320 168]]
   :up-left [[352 8] [352 40] [352 72] [352 104] [352 136] [352 168]]

})

(def sheep-frames
  {:right
   {:look-away [192 264]
    :look-towards [192 288]
    :stand [192 312]
    :hop [192 336]
    :munch1 [192 360]
    :minch2 [192 384]
    :dead  [192 408]}

   :left
   {:look-away [224 264]
    :look-towards [224 288]
    :stand [224 312]
    :hop [224 336]
    :munch1 [224 360]
    :minch2 [224 384]
    :dead  [224 408]}

   :blood
   {0 [256 264]
    1 [256 288]
    2 [256 312]
    3 [245 336]}
   }

  )

(def arrows
  {:left [296 256]
   :down [296 280]
   :right [296 304]
   :up [296 328]
   :up-left [320 256]
   :down-left [320 280]
   :down-right [320 304]
   :up-right [320 328]
   })

(def game-props
  {
   :tree1 [[72 120] [48 48]]
   :tree2 [[384 152] [64 104]]
   :rock [[16 160] [8 8]]
   :rock2 [[24 160] [16 8]]
   :grass1 [[16 168] [8 8]]
   :grass2 [[24 168] [8 8]]
   :grass3 [[32 168] [8 8]]
   :grass4 [[40 168] [8 8]]
   :grass5 [[48 168] [8 8]]
   :grass6 [[56 168] [8 8]]
   })

(def abundance
  {
   :grass1 100
   :grass2 1000
   :grass3 1000
   :grass4 1000
   :grass5 1000
   :grass6 1000

   :rock 200
   :rock2 200
   :tree1 100
   :tree2 500})

(def level
  (into []
        (apply concat (for [[prop num] abundance]
                   (take num (repeatedly (fn [] [prop [(math/rand-between -5000 5000)
                                                       (math/rand-between -5000 5000)]])))
                   ))))


(println "LEVEL:")
(println level)

(defn make-prop-texture-lookup [props]
  (let [spritesheet (resources/get-texture :sprites :nearest)]
    (into {}
          (for [[prop-name [pos size]] props]
            [prop-name
             (texture/sub-texture spritesheet pos size)]))))

(def bounce-length 120)
(def bounce-height 40)

(defn bounce-sheep [pos unit]
  (let [c (chan)
        time 20]
    (go
      (loop [n 0]
        (let [dest (-> unit
                       (vec2/scale bounce-length)
                       (vec2/add pos))
              travel (vec2/sub dest pos)
              param (/ n time)
              across (vec2/scale travel param)
              parabola (- (* 4 param) (* 4 param param))
              up (vec2/vec2 0 (- (* bounce-height parabola)))
              total (vec2/add up across)]
          (if (<= n time)
            (do
              (>! c [(vec2/add pos total)
                     (if (< (vec2/get-x unit) 0) :left :right)
                     :hop])
              (recur (inc n)))

            ;; stand for a bit
            (do
              (loop [n 20]
                (when (pos? n)
                  (>! c [(vec2/add pos total)
                         (if (< (vec2/get-x unit) 0) :left :right)
                         :stand])
                  (recur (dec n))))

              (close! c))))))
    c))

(defonce player-atom (atom {:pos (vec2/zero)
                            :eating 0}))

(defonce sheep-atom (atom #{}))

(defn depth-compare [a b]
  (cond
    (< (.-position.y a) (.-position.y b)) -1
    (< (.-position.y b) (.-position.y a)) 1
    :default 0
    ))

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
          arrow-tex (into {}
                          (for [[k v] arrows]
                            [k (texture/sub-texture spritesheet v [24 24])]))

          sheep-tex (into
                     {}
                     (for [[k v] sheep-frames]
                       [k (into
                           {}
                           (for [[kk pos] v]
                             [kk (texture/sub-texture spritesheet pos [24 16])]))]))]
      (go
        (let [props (make-prop-texture-lookup game-props)]
          ;; make world
          (macros/with-sprite-set canvas :world
            [sprites (doall (for [[prop [x y]] level]
                              (sprite/make-sprite (prop props)
                                                  :x x :y y
                                                  :scale scale
                                                  :xhandle 0.5 :yhandle 0.9)))]
                                        ;(log "!" (last sprites))

            ;; make player
            (macros/with-sprite canvas :world
              [player (sprite/make-sprite (-> player-tex :left first) :scale scale
                                          :xhandle 0.5 :yhandle 0.8)]

              ;; HUD arrow go block
              (go
                (macros/with-sprite canvas :ui
                  [arrow (sprite/make-sprite
                          (-> arrow-tex :left)
                          :scale scale
                          :xhandle 0.5
                          :yhandle 0.5
                          :x 100
                          :y 100
                          )]

                  (loop []
                    (let [sheep-pos (map (fn [spr] (vec2/vec2 (.-position.x spr)
                                                              (.-position.y spr)))
                                         @sheep-atom)
                          sheepies @sheep-atom
                          player-pos (:pos @player-atom)

                          disp (if (= 0 (count sheep-pos))
                                 (:pos @player-atom)

                                 ;; minimum sheep distance
                                 (do
                                   (let [msd
                                         (first (sort-by first (for [sh sheep-pos] [(vec2/distance player-pos sh) sh])))
                                         [dist spos] msd
                                        ]
                                     (vec2/sub (second msd) player-pos))))
                          sw (.-innerWidth js/window)
                          sh (.-innerHeight js/window)
                          hw (/ sw 2)
                          hh (/ sh 2)
                          x (vec2/get-x disp)
                          y (vec2/get-y disp)
                          buff 24
                          fhw (- hw buff)
                          fhh (- hh buff)
                          m (/ fhh fhw)
                          pos (or
                               (if (> x fhw)
                                 (if
                                     (and
                                      (> y (* (- m) x))
                                      (< y (* m x)))
                                   (let [m2 (/ y x)]
                                     [fhw (* m2 fhw) :right])))
                               (if (< x (- fhw))
                                 (if
                                     (and
                                      (> y (* m x))
                                      (< y (* (- m) x)))
                                   (let [m2 (/ y x)]
                                     [(- fhw) (* m2 (- fhw)) :left])))
                               (if (> y fhh)
                                 (if (and  (> x (/ y (- m)))
                                           (< x (/ y m)))
                                   (let [m2 (/ x y)]
                                     [(* m2 fhh) fhh :down])))
                               (if (< y (- fhh))
                                 (if (and  (> x (/ y m))
                                           (< x (/ y (- m))))
                                   (let [m2 (/ x y)]
                                     [(* m2 (- fhh)) (- fhh) :up]))
                                 )
                               )
                          [x y frame] pos
                          ]
                      (do (if pos
                            (do
                              (sprite/set-pos! arrow x y)
                              (sprite/set-alpha! arrow 1.0)
                              (sprite/set-texture! arrow (-> arrow-tex frame))
                              )
                            (sprite/set-alpha! arrow 0.0))
                          )

                      #_ (case sector
                           :top-right
                           (do (sprite/set-pos! arrow (vec2/vec2 (- hw buff) (- buff hh)))
                               (sprite/set-texture! arrow (-> arrow-tex :up-right)))

                           :top-left
                           (do (sprite/set-pos! arrow (vec2/vec2 (- buff hw) (- buff hh)))
                               (sprite/set-texture! arrow (-> arrow-tex :up-left)))


                           :bottom-left
                           (do (sprite/set-pos! arrow (vec2/vec2 (- buff hw) (- hh buff)))
                               (sprite/set-texture! arrow (-> arrow-tex :down-left)))

                           :bottom-right
                           (do (sprite/set-pos! arrow (vec2/vec2 (- hw buff) (- hh buff)))
                               (sprite/set-texture! arrow (-> arrow-tex :down-right)))

                           :left
                           (do (sprite/set-pos! arrow (-> disp vec2/unit (vec2/scale hw) (vec2/add (vec2/vec2 buff 0))))
                               (sprite/set-texture! arrow (-> arrow-tex :left)))


                           (sprite/set-pos! arrow disp))
                                        ;(log (str sector))
                      )
                    (<! (events/next-frame))
                    (recur))))

              (<! (resources/fadein player :duration 0.5))

              (go (dotimes [n 20]
                    (<! (events/wait-time 200))

                    ;; spawn a sheep
                    (go

                      (macros/with-sprite canvas :world
                        [sheep (sprite/make-sprite (-> sheep-tex :left :stand)
                                                   :scale scale
                                                   :xhandle 0.5
                                                   :yhandle 0.9)]
                        (swap! sheep-atom conj sheep)
                        (loop [[pos alive] [(vec2/scale (vec2/random) 3000) true]]
                          (when alive
                            (let [ch (bounce-sheep
                                      pos
                                      (vec2/random-unit)
                                      )]
                              (recur
                               (loop [nex (<! ch) pos pos]
                                 (if nex
                                   (let [[pos dir frame] nex]
                                     (sprite/set-pos! sheep pos)
                                     (sprite/set-texture! sheep (-> sheep-tex dir frame))
                                     (<! (events/next-frame))

                                     ;; collision with player?
                                     (when
                                         (<
                                          (vec2/distance pos (:pos @player-atom))
                                          20)
                                       (log "Sheep caught")
                                       (swap! sheep-atom disj sheep)
                                       (sprite/set-texture! sheep (-> sheep-tex dir :dead))
                                       (swap! player-atom update-in [:eating] inc)
                                       (<! (events/wait-time 10000))
                                       (<! (resources/fadeout sheep :duration 60))
                                       [pos false])


                                     (recur (<! ch) pos))
                                   [pos true]))))))

))))

              ;; run game
              (loop [pos (vec2/zero)
                     vel (vec2/vec2 -6 0)
                     n 0]
                (let [next-pos (vec2/add pos vel)
                      x (aget next-pos 0)
                      y (aget next-pos 1)]



                  (sprite/set-pivot! (-> canvas :layer :world)  x y)
                  (sprite/set-pos! player x y)
                  (swap! player-atom assoc :pos (vec2/vec2 x y))

                  ;; set animation frame
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



                    (when (> (:eating @player-atom) 0)
                      ;;
                      ;; eating
                      ;;
                                        ;(sprite/set-pos! player (vec2/sub (vec2/vec2 x y) (vec2/zero))

                      (loop [n 10]
                        (go
                          (macros/with-sprite canvas :ui
                            [blood (sprite/make-sprite ((-> sheep-tex :blood) 0)
                                                       :scale scale
                                                       :x 0
                                                       :y 0
                                                       :xhandle 0.5
                                                       :yhandle 1.1)]

                            (loop [n 1]
                              (<! (events/wait-time 100))
                              (sprite/set-texture! blood ((-> sheep-tex :blood) n))
                              (when (< n 3) (recur (inc n))))))

                        (sprite/set-texture! player
                                             (nth (-> player-tex frame) 4))
                        (<! (events/wait-time (math/rand-between 100 400)))
                        (sprite/set-texture! player
                                             (nth (-> player-tex frame) 5))
                        (<! (events/wait-time (math/rand-between 100 400)))
                        (when (pos? n)
                          (recur (dec n))))



                                        ;(<! (events/wait-time 1000))
                      (swap! player-atom update-in [:eating] dec)
                      (recur (vec2/vec2 x y) (vec2/scale (vec2/unit vel) 0.2) n))



                    (sprite/set-texture! player (nth
                                                 (-> player-tex frame)
                                                 (mod (int (* 0.02 n)) 4))))




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
                           (->
                            ;; players acceleration vector
                            (if (and (= x 0) (= y 0))
                              (vec2/vec2 x y)
                              (vec2/scale (vec2/unit (vec2/vec2 x y))
                                          1.5))

                            ;; add to old velocity
                            (vec2/add (vec2/scale vel 0.98))

                            ;; maximum velocity
                            (vec2/truncate 10)))

                         (+ n (vec2/magnitude vel)))))

              (<! (resources/fadeout player :duration 0.5)))
            (<! (events/wait-time 200000))))))))


(defonce _ (main))
