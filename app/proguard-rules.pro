# RoomReset Mini — R8 rules (used only with -ProomresetMinify=true).
# Room, DataStore, Navigation, Lifecycle and Compose ship their own consumer rules.
# Enums are stored by name in Room columns and resolved with valueOf-style lookups
# over `entries`, which R8 keeps because the code references them directly.

# Keep source file / line numbers for readable crash traces with the mapping file.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
