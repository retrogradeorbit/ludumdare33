# ludumdare33

An entry to ludumdare 33 game jam. HTML5 game playable in the browser
written in clojurescript. Theme was "You are the Monster".

Play it here

http://ld33.procrustes.net

## Overview

Use cursor keys to control your monster. Eat as many sheep as you can.

## Prerequisites

You need to install infinitelives.utils and infinitelives.pixi to
build and run this.

Go to infinitelives.utils checkout. Do a lein install.

    git clone git@github.com:infinitelives/infinitelives.utils.git
    cd infinitelives.utils
    lein cljsbuild test
    lein install

Go to infinitelives.pixi checkout. Do a lein install.

    git clone git@github.com:infinitelives/infinitelives.pixi.git
    cd infinitelives.pixi
    lein cljsbuild test
    lein install

## Setup

To get an interactive development environment run:

    lein figwheel

and open your browser at [localhost:3449](http://localhost:3449/).
This will auto compile and send all changes to the browser without the
need to reload. After the compilation process is complete, you will
get a Browser Connected REPL. An easy way to try it is:

    (js/alert "Am I connected?")

and you should see an alert in the browser window.

To clean all compiled files:

    lein clean

To create a production build run:

    lein cljsbuild once min

And open your browser in `resources/public/index.html`. You will not
get live reloading, nor a REPL.

## License

Copyright © 2015 Crispin Wellington

Distributed under the Eclipse Public License version 1.0.
