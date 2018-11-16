(ns status-im.ui.screens.hardwallet.success.styles
  (:require [status-im.ui.components.colors :as colors]))

(def container
  {:flex             1
   :background-color colors/white})

(def inner-container
  {:flex-direction  :column
   :flex            1
   :align-items     :center
   :justify-content :space-between})

(def hardwallet-card-image-container
  {:margin-top      120
   :flex-direction  :column
   :align-items     :center
   :justify-content :center})

(def hardwallet-card-image
  {:width  255
   :height 160})

(def icon-check-container
  {:width            160
   :height           160
   :align-items      :center
   :justify-content  :center
   :background-color colors/green-transparent-10
   :border-radius    100})

(def icon-check-inner-container
  {:width            80
   :height           80
   :align-items      :center
   :justify-content  :center
   :background-color colors/white
   :border-radius    50})

(def complete-text-container
  {:margin-top 40})

(def complete-text
  {:font-size   22
   :font-weight :bold
   :color       colors/black
   :text-align  :center})

(def complete-information-text
  {:text-align         :center
   :font-size          15
   :line-height        15
   :color              colors/gray
   :margin-bottom      21
   :padding-horizontal 80
   :padding-vertical   10})

(def bottom-action-container
  {:background-color colors/gray-background
   :align-items      :center
   :justify-content  :center
   :flex-direction   :row
   :width            104
   :height           44
   :border-radius    10
   :margin-bottom    40})

(def bottom-action-text
  {:font-size      14
   :color          colors/blue
   :line-height    20
   :text-transform :uppercase})