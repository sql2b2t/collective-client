package me.collective.client.render;

public interface RenderState {
   default RenderState then(RenderState next) {
      return entering -> {
         if (entering) {
            this.apply(true);
            next.apply(true);
         } else {
            next.apply(false);
            this.apply(false);
         }
      };
   }

   static RenderState combine(RenderState... states) {
      return entering -> {
         if (entering) {
            for (RenderState state : states) {
               state.apply(true);
            }
         } else {
            for (int index = states.length - 1; index >= 0; index--) {
               states[index].apply(false);
            }
         }
      };
   }

   void apply(boolean entering);
}
