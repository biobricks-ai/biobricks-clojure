(ns biobricks.web-ui.api.routes)

(def routes
  [["/" ["" :home]]
   ["/u"
    ["/:org-name"
     ["/:brick-name"
      ["" :biobrick]
      ["/badge"
       ["/health" :brick-badge-health]]]]]])
