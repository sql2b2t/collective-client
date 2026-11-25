package me.collective.client.utils.world

enum class Season(vararg monthValues: Int) {
   WINTER(1, 2, 12),
   SPRING(3, 4, 5),
   SUMMER(6, 7, 8),
   AUTUMN(9, 10, 11);

   private val months: List<Int> = monthValues.toList()

   fun containsMonth(month: Int): Boolean {
      return months.contains(month)
   }

   companion object {
      @JvmStatic
      fun fromMonth(month: Int): Season {
         for (season in entries) {
            if (season.containsMonth(month)) {
               return season
            }
         }

         throw IllegalArgumentException("Unknown month")
      }
   }
}
