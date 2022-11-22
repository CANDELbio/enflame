(require 'org)
(require 'ox-html)

(setq org-export-with-broken-links 'mark)

(setq org-publish-project-alist
  '(("guide"
     :base-directory "doc/"
     :publishing-directory "doc/"
     :publishing-function org-html-publish-to-html
     )))

(org-publish "guide" t)
