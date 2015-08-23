release:
	lein cljsbuild once release
	cp -a resources/public/img target/release/img
	cp -a resources/public/sfx target/release/sfx

	#for FILE in pixi.dev.js hammer.min.js; \
	#	do cp resources/public/js/$$FILE target/release/js/; \
	#done
	cp -a resources/public/css target/release/css
	cp resources/index.html target/release/

server:
	cd resources/public && python -m SimpleHTTPServer

clean:
	rm -rf target

upload:
	rsync -av resources/public/ www-data@atomsk.procrustes.net:~/ld33.procrustes.net/public_html/

archive:
	cd resources/public && tar cvzf ../../ld33.procrustes.net.tar.gz .

resources/public/img/sprites.png: src/gfx/sprites.png
	convert src/gfx/sprites.png -alpha On -transparent '#2ec13c' resources/public/img/sprites.png

images: resources/public/img/sprites.png

sfx: src/sfx/gong.wav
	cd src/sfx && oggenc *.wav

	#-mkdir resources/public/sfx/
	cp src/sfx/*.ogg resources/public/sfx/
