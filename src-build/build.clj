(ns build
  "build electric.jar library artifact and demos"
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]
            [shadow.cljs.devtools.api :as shadow-api] ; so as not to shell out
                                                      ; to NPM for shadow
            [shadow.cljs.devtools.server :as shadow-server]))

(def lib 'ai.biobricks/biobricks-web)
(def version
  (b/git-process {:git-args "describe --tags --long --always --dirty"}))
(def basis (b/create-basis {:project "deps.edn"}))

(defn clean [opts] (bb/clean opts))

(def class-dir "target/classes")
(defn default-jar-name
  [{:keys [version], :or {version version}}]
  (format "target/%s-%s-standalone.jar" (name lib) version))

(defn clean-cljs [_] (b/delete {:path "bases/web-ui/resources/public/js"}))

(defn build-client
  "Prod optimized ClojureScript client build. (Note: in dev, the client is built 
on startup)"
  [{:keys [optimize debug verbose version],
    :or {optimize true, debug false, verbose false, version version}}]
  (println "Building client. Version:" version)
  (shadow-server/start!)
  (shadow-api/release
    :prod
    {:debug debug,
     :verbose verbose,
     :config-merge
     [{:compiler-options {:optimizations (if optimize :advanced :simple)},
       :closure-defines {'hyperfiddle.electric-client/VERSION version}}]})
  (shadow-server/stop!))

(defn build-css [_]
  (b/process {:command-args ["bash" "bin/build-css"]}))

(defn uberjar
  [{:keys [jar-name version optimize debug verbose],
    :or {version version, optimize true, debug false, verbose false}}]
  (println "Cleaning up before build")
  (clean nil)
  (println "Cleaning cljs compiler output")
  (clean-cljs nil)
  (build-client
    {:optimize optimize, :debug debug, :verbose verbose, :version version})
  ; CSS should come after JS because Tailwind analyzes the classes in the JS
  (println "Building CSS")
  (build-css nil)
  (println "Bundling resources")
  (b/copy-dir {:src-dirs ["bases/web-ui/resources"], :target-dir class-dir})
  (println "Compiling server. Version:" version)
  (b/compile-clj {:basis basis,
                  :src-dirs ["src"],
                  :ns-compile '[biobricks.web-ui.prod],
                  :class-dir class-dir})
  (println "Building uberjar")
  (b/uber {:class-dir class-dir,
           :uber-file (str (or jar-name (default-jar-name {:version version}))),
           :basis basis,
           :main 'biobricks.web-ui.prod}))

(defn noop [_])                         ; run to preload mvn deps
