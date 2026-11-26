package me.collective.client.render

fun interface RenderState {
    fun then(next: RenderState): RenderState {
        return RenderState { entering ->
            if (entering) {
                this.apply(true)
                next.apply(true)
            } else {
                next.apply(false)
                this.apply(false)
            }
        }
    }

    fun apply(entering: Boolean)

    companion object {
        @JvmStatic
        fun combine(vararg states: RenderState): RenderState {
            return RenderState { entering ->
                if (entering) {
                    for (state in states) {
                        state.apply(true)
                    }
                } else {
                    for (index in states.indices.reversed()) {
                        states[index].apply(false)
                    }
                }
            }
        }
    }
}
