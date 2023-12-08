(ns biobricks.ui-table.ifc
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]))

; Subject to https://tailwindui.com/license

; https://tailwindui.com/components/application-ui/lists/tables#component-7b221bde27fd63a91847edaf2e06b3c3
(e/defn Container [title thead tbody]
  (e/client
    (dom/div (dom/props {:class "bg-gray-900"})
      (dom/div (dom/props {:class "mx-auto max-w-7x1"})
        (dom/div (dom/props {:class "bg-gray-900 py-10"})
          (dom/div (dom/props {:class "px-4 sm:px-6 lg:px-8"})
            (dom/div (dom/props {:class "sm:flex sm:items-center"})
              (dom/div (dom/props {:class "sm:flex-auto"})
                (dom/h1 (dom/props {:class "text-base font-semibold leading-6 text-white"})
                  (dom/text title))))
            (dom/div (dom/props {:class "sm:flex mt-8 flow-root"})
              (dom/div (dom/props {:class "-my-2 overflow-x-auto"})
                (dom/div (dom/props {:class "inline-block min-w-full py-2 align-middle sm:px-6 lg:px-8"}))
                (dom/table (dom/props {:class "min-w-full divide-y divide-gray-700"})
                  (new thead)
                  (new tbody))))))))))

(e/defn Head [labels]
  (e/client
    (dom/thead
      (dom/tr
        (dom/th (dom/props {:scope "col" :class "py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-white sm:pl-0"})
          (dom/text (first labels)))
        (e/for [label (rest labels)]
          (dom/th (dom/props {:scope "col" :class "px-3 py-3.5 text-left text-sm font-semibold text-white"})
            (dom/text label)))))))

(e/defn Body [body]
  (e/client
    (dom/tbody (dom/props {:class "divide-y divide-gray-800"})
      body)))

(e/defn Row [values]
  (e/client
    (dom/tr
      (dom/td (dom/props {:class "whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-white sm:pl-0"})
        (let [v (first values)]
          (dom/text v)))
      (e/for [v (rest values)]
        (dom/td (dom/props {:class "whitespace-nowrap px-3 py-4 text-sm text-gray-300"})
          (if (fn? v)
            (new v)
            (dom/text v)))))))
