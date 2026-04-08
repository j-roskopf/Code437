package com.cardgame.art

/**
 * ASCII art for slot machine reel symbols — full 16×30 pieces as provided.
 * Each piece is exactly 16 lines tall × 30 characters wide (space-padded).
 */
object SlotMachineArt {

    val ROCKET_LINES: List<String> = """
        |                              
        |                              
        |                 █████████░   
        |                  ░███████░   
        |               ██   ░█████░   
        |             ████ ░█▓ ▒███░   
        |             ████ ░███▒ ▓█░   
        |         ▓█░ ████ ░███▒       
        |       ▒███░ ████ ░█▓         
        |         ▓█░ ████             
        |         ▓█░ ████             
        |       ▒█▒   ██               
        |     ░█▒                      
        |   ░█▓                        
        |                              
        |                              
    """.trimMargin().lines()

    val APPLE_LINES: List<String> = """
        |                              
        |                              
        |                 ███▓         
        |               █████▓         
        |               ██             
        |       ▒█████░   █████▒       
        |     ░██████████████████▒     
        |     ░███████████   ░███▒     
        |     ░███████████   ░███▒     
        |     ░██████████████████▒     
        |     ░██████████████████▒     
        |       ▒██████████████▒       
        |       ▒██████████████▒       
        |         ▓███░   ███▓         
        |                              
        |                              
    """.trimMargin().lines()

    val CARROT_LINES: List<String> = """
        |                              
        |                              
        |                 ███▓ ▒███░   
        |                 ███▓ ▒███░   
        |                    ░█▒       
        |             ██████   ▒███░   
        |           ▓████████▓ ▒███░   
        |           ▓████████▓         
        |         ▓██████████▓         
        |       ▒███████████           
        |       ▒███████               
        |     ░███████░                
        |   ░█████▒                    
        |   ░███▒                      
        |                              
        |                              
    """.trimMargin().lines()

    val PEAR_LINES: List<String> = """
        |                              
        |                 █████▒       
        |             ████   ░███▒     
        |           ▓███████ ░███▒     
        |           ▓███████           
        |         ▓██████████▓         
        |         ▓█████   ░█▓         
        |       ▒███████   ░███▒       
        |     ░██████████████████▒     
        |     ░██████████████████▒     
        |     ░██████████████████▒     
        |     ░██████████████████▒     
        |     ░██████████████████▒     
        |       ▒██████████████▒       
        |           ▓███████           
        |                              
    """.trimMargin().lines()

    val QUESTION_LINES: List<String> = """
        |                              
        |                              
        |                              
        |           ▓███████           
        |         ▓██████████▓         
        |         ▓███░   ███▓         
        |                 ███▓         
        |             ███████▓         
        |             ██████           
        |             ████             
        |                              
        |             ████             
        |             ████             
        |                              
        |                              
        |                              
    """.trimMargin().lines()

    val FOSSIL_LINES: List<String> = """
        |                              
        |                              
        |                              
        |     ░███████████             
        |   ░█████▒   ██████           
        |   ░█████▒     █████████▒     
        |   ░███████░   █████▓ ▒███░   
        |     ░████████████████████░   
        |   ░█▓       █████████████░   
        |     ░█████░ ██  ██ ░█▒ ▓█░   
        |     ░█████░   ██ ░█▓ ▒█▒     
        |       ▒██████████████████░   
        |           ▓████████████▒     
        |                              
        |                              
        |                              
    """.trimMargin().lines()

    val TOILET_LINES: List<String> = """
        |                              
        |                              
        |     ░█████░                  
        |     ░█▒ ▓█░                  
        |     ░█▒ ▓█░                  
        |     ░█▒ ▓████████████▒       
        |     ░█▒ ▓███░      ░███▒     
        |     ░█▒ ▓█░ ███████▓ ▒█▒     
        |     ░█████░ ███████▓ ▒█▒     
        |     ░███████░      ░███▒     
        |     ░███▒ ▓██████████▒       
        |       ▒███░                  
        |       ▒██████████████▒       
        |         ▓██████████▓         
        |                              
        |                              
    """.trimMargin().lines()

    /** Stylized "7" — top bar + diagonal stem (replaces former dice tile). */
    val SEVEN_LINES: List<String> = """
        |                              
        |                              
        |                              
        |       ▒██████████████▒       
        |       ▒██████████████▒       
        |                  ░███▒       
        |                 █████▒       
        |               █████▓         
        |             ██████           
        |             ████             
        |             ████             
        |             ████             
        |             ████             
        |                              
        |                              
        |                              
    """.trimMargin().lines()

    enum class Symbol(val label: String, val lines: List<String>) {
        ROCKET("ROCKET", ROCKET_LINES),
        APPLE("APPLE", APPLE_LINES),
        CARROT("CARROT", CARROT_LINES),
        PEAR("PEAR", PEAR_LINES),
        QUESTION("?", QUESTION_LINES),
        FOSSIL("FOSSIL", FOSSIL_LINES),
        TOILET("TOILET", TOILET_LINES),
        DICE("7", SEVEN_LINES),
    }
}
