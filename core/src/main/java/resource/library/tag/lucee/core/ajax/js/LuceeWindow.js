Lucee.Window=(function(){var a=new Lucee.adapters.Window();function b(){return a}return{getWindowObject:function(c){return b().getWindowObject(c)},windowExists:function(c){return b().windowExists(c)},create:function(g,h,f,e,i){var c=b();Lucee.Events.dispatchEvent("Window.beforeCreate",arguments);c.create(g,h,f,e,i);var d=this.getWindowObject(g);Lucee.Events.dispatchEvent("Window.afterCreate",d);if(e.initShow){this.show(g)}},show:function(e){var c=b();var d=this.getWindowObject(e);Lucee.Events.dispatchEvent("Window.beforeShow",d);c.show(e);Lucee.Events.dispatchEvent("Window.afterShow",d)},hide:function(e){var c=b();var d=this.getWindowObject(e);Lucee.Events.dispatchEvent("Window.beforeHide",d);c.hide(e);Lucee.Events.dispatchEvent("Window.afterHide",d)},onHide:function(d,e){var c=b();c.onHide(d,e)},onShow:function(d,e){var c=b();c.onShow(d,e)}}})();