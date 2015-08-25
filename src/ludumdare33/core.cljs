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
(def num-sheep-start 10)
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
                            :eating 0
                            :kills 0}))

(defonce sheep-atom (atom #{}))

(defn depth-compare [a b]
  (cond
    (< (.-position.y a) (.-position.y b)) -1
    (< (.-position.y b) (.-position.y a)) 1
    :default 0
    ))

(defn spawn-sheep [sheep-tex sfx-cute sfx-growl]
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

            ;; sfx
            (let [distance (vec2/distance (:pos @player-atom) pos)]
              (sound/play-sound (rand-nth sfx-cute) (min (/ 30. distance) 0.5) false))

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

                     (sound/play-sound sfx-growl 0.7 false)
                     (swap! player-atom update-in [:kills] inc)

                     (swap! sheep-atom disj sheep)
                     (sprite/set-texture! sheep (-> sheep-tex dir :dead))
                     (swap! player-atom update-in [:eating] inc)
                     (<! (events/wait-time 10000))
                     (<! (resources/fadeout sheep :duration 60))
                     (spawn-sheep sheep-tex sfx-cute sfx-growl)
                     [pos false])


                   (recur (<! ch) pos))
                 [pos true]))))))

      )))

(defn main []
  (go
    (<! (resources/load-resources
         (-> canvas :layer :ui)
         ["img/sprites.png"
          "sfx/ld33.ogg"
          "sfx/cute.ogg"
          "sfx/weep.ogg"
          "sfx/weep2.ogg"
          "sfx/weep3.ogg"
          "sfx/growl.ogg"
          "http://fonts.gstatic.com/s/amaticsc/v7/DPPfSFKxRTXvae2bKDzp5FtXRa8TVwTICgirnJhmVJw.woff2"
          ]
         :full-colour 0x6ecb64          ;0x005217
         :highlight 0x6ecb64
         :lowlight 0x0f7823
         :empty-colour 0x2ec13c
         :debug-delay 0.2
         :width 400
         :height 32
         :fade-in 0.2
         :fade-out 0.5))

    (go (let [tune (<! (sound/load-sound "/sfx/ld33.ogg"))
              [source gain] (sound/play-sound tune 0.4 true)
              ])
        )

    (let [spritesheet (resources/get-texture :sprites :nearest)
          sfx-growl (<! (sound/load-sound "/sfx/growl.ogg"))
          sfx-cute [(<! (sound/load-sound "/sfx/bounce.ogg"))
                    (<! (sound/load-sound "/sfx/bounce2.ogg"))
                    (<! (sound/load-sound "/sfx/bounce3.ogg"))
                    (<! (sound/load-sound "/sfx/bounce4.ogg"))]
          myfont (font/make-tiled-font "Amatic SC" 400 50)
          tspr1 (font/make-text "400 50px Amatic SC"
                                "Some test text"
                                :weight 400 :fill "#ffffff"
                                :dropShadow true
                                :dropShadowColor "#000000"
                                :dropShadowDistance 1)
          tspr2 (font/make-text "400 50px Amatic SC"
                                "Some test text"
                                :weight 400 :fill "#ffffff"
                                :dropShadow true
                                :dropShadowColor "#000000"
                                :dropShadowDistance 1)
          _ (sprite/set-pos! tspr1 -100000 1000000)
          _ (.addChild (-> canvas :layer :world) tspr1)
          delay (<! (timeout 500))


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

      (go (loop []
            (macros/with-sprite canvas :ui

              [text
               (font/make-text "400 50px Amatic SC"
                               (str (:kills @player-atom))
                               :weight 400 :fill "#ffffff"
                               :dropShadow true
                               :dropShadowColor "#000000"
                               :dropShadowDistance 1
                               :x (-> js/window .-innerWidth (/ 2) (- 100))
                               :y (-> js/window .-innerHeight (/ 2) (- 100))
                              )]
              (sprite/set-scale! text 2)
              (loop []
                (let [kills (:kills @player-atom)]
                  (.setText text (str kills))
                  (sprite/set-pos! text
                                   (-> js/window .-innerWidth (/ 2) (- 100))
                                   (-> js/window .-innerHeight (/ 2) (- 100)))
                  (<! (events/wait-time 100))
                  (recur))))))

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

              (go

                (dotimes [n num-sheep-start]
                    (<! (events/wait-time 200))

                    ;; spawn a sheep
                    (spawn-sheep sheep-tex sfx-cute sfx-growl)
                    ))

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
                              (.sort (.-children (-> canvas :layer :world)) depth-compare )
                              (<! (events/wait-time 100))
                              (sprite/set-texture! blood ((-> sheep-tex :blood) n))
                              (when (< n 3) (recur (inc n))))))

                        (sprite/set-texture! player
                                             (nth (-> player-tex frame) 4))
                        (.sort (.-children (-> canvas :layer :world)) depth-compare )
                        (<! (events/wait-time (math/rand-between 100 400)))
                        (sprite/set-texture! player
                                             (nth (-> player-tex frame) 5))
                        (.sort (.-children (-> canvas :layer :world)) depth-compare )
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
