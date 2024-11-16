(ns redelay.core)

(defmacro state [& exprs]
  (reduce #(conj %1 (cond-> %2 (= (last %1) :name) str)) [] exprs))
