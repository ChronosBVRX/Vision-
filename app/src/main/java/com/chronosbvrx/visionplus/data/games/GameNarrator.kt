package com.chronosbvrx.visionplus.data.games

import com.chronosbvrx.visionplus.R
import kotlin.random.Random

/** Personalidad y voz de la narradora. Los assets viven en `res/drawable-nodpi` y `res/raw`. */
enum class NarratorMood { Idle, Welcome, Thinking, Happy, Oops, Celebrate }

enum class NarratorEvent {
    Welcome, Selector, RoomCreated, Joined, Started, Correct, Wrong, TimeUp, Reveal, Podium, Solo,
    CharadaTurn, CharadaCorrect, CharadaPass, CharadaReveal,
    WagerStart, WagerClue, WagerReveal, WagerWin, WagerLose,
    ImpostorSecret, ImpostorVote, ImpostorCaught, DrawStart, DrawVote,
    AuctionStart, AuctionReveal, FibbageWrite, FibbageVote,
    ImpostorQuestions, ImpostorEscaped, PartyTimeout, AuctionTie,
    LieSweep, NobodyGuessed, FinalRoundDouble,
}

object GameNarrator {

    fun moodFor(event: NarratorEvent): NarratorMood = when (event) {
        NarratorEvent.Welcome, NarratorEvent.Selector, NarratorEvent.RoomCreated,
        NarratorEvent.Joined -> NarratorMood.Welcome
        NarratorEvent.Started, NarratorEvent.Correct, NarratorEvent.CharadaCorrect,
        NarratorEvent.WagerWin, NarratorEvent.AuctionReveal -> NarratorMood.Happy
        NarratorEvent.Wrong, NarratorEvent.TimeUp, NarratorEvent.CharadaPass,
        NarratorEvent.WagerLose, NarratorEvent.ImpostorCaught,
        NarratorEvent.PartyTimeout, NarratorEvent.NobodyGuessed -> NarratorMood.Oops
        NarratorEvent.Reveal, NarratorEvent.CharadaTurn, NarratorEvent.CharadaReveal,
        NarratorEvent.WagerClue, NarratorEvent.ImpostorSecret, NarratorEvent.ImpostorVote,
        NarratorEvent.DrawStart, NarratorEvent.DrawVote, NarratorEvent.AuctionStart,
        NarratorEvent.FibbageWrite, NarratorEvent.FibbageVote,
        NarratorEvent.ImpostorQuestions -> NarratorMood.Thinking
        NarratorEvent.Podium, NarratorEvent.WagerReveal,
        NarratorEvent.ImpostorEscaped, NarratorEvent.AuctionTie,
        NarratorEvent.LieSweep, NarratorEvent.FinalRoundDouble -> NarratorMood.Celebrate
        NarratorEvent.WagerStart, NarratorEvent.Solo -> NarratorMood.Idle
    }

    /** Texto y audio por momento. Diez variantes cada uno para no repetirse.
     * Los textos viven en `tools/narrator_lines.json` y los audios en
     * `res/raw/narrator_<id>_<n>.m4a` (este bloque se genera desde ese JSON). */
    data class NarratorVariant(val text: String, val audio: Int)

    fun variantId(event: NarratorEvent): String = when (event) {
        NarratorEvent.Welcome -> "welcome"
        NarratorEvent.Selector -> "selector"
        NarratorEvent.RoomCreated -> "create"
        NarratorEvent.Joined -> "join"
        NarratorEvent.Started -> "start"
        NarratorEvent.Correct -> "correct"
        NarratorEvent.Wrong -> "wrong"
        NarratorEvent.TimeUp -> "time"
        NarratorEvent.Reveal -> "reveal"
        NarratorEvent.Podium -> "podium"
        NarratorEvent.Solo -> "solo"
        NarratorEvent.CharadaTurn -> "charada_turn"
        NarratorEvent.CharadaCorrect -> "charada_correct"
        NarratorEvent.CharadaPass -> "charada_pass"
        NarratorEvent.CharadaReveal -> "charada_reveal"
        NarratorEvent.WagerStart -> "wager_start"
        NarratorEvent.WagerClue -> "wager_clue"
        NarratorEvent.WagerReveal -> "wager_reveal"
        NarratorEvent.WagerWin -> "wager_win"
        NarratorEvent.WagerLose -> "wager_lose"
        NarratorEvent.ImpostorSecret -> "impostor_secret"
        NarratorEvent.ImpostorVote -> "impostor_vote"
        NarratorEvent.ImpostorCaught -> "impostor_caught"
        NarratorEvent.DrawStart -> "draw_start"
        NarratorEvent.DrawVote -> "draw_vote"
        NarratorEvent.AuctionStart -> "auction_start"
        NarratorEvent.AuctionReveal -> "auction_reveal"
        NarratorEvent.FibbageWrite -> "fibbage_write"
        NarratorEvent.FibbageVote -> "fibbage_vote"
        NarratorEvent.ImpostorQuestions -> "impostor_questions"
        NarratorEvent.ImpostorEscaped -> "impostor_escaped"
        NarratorEvent.PartyTimeout -> "party_timeout"
        NarratorEvent.AuctionTie -> "auction_tie"
        NarratorEvent.LieSweep -> "lie_sweep"
        NarratorEvent.NobodyGuessed -> "nobody_guessed"
        NarratorEvent.FinalRoundDouble -> "final_round_double"
    }

    fun variantsFor(event: NarratorEvent): List<NarratorVariant> = when (event) {
        NarratorEvent.Welcome -> listOf(
            NarratorVariant("¡Hola! Soy la narradora de Vision+ Play. ¿Le entramos a unos juegos?", R.raw.narrator_welcome_0),
            NarratorVariant("¡Buenas, buenas! Ya llegaste a Vision+ Play. ¿Jugamos o qué?", R.raw.narrator_welcome_1),
            NarratorVariant("¡Bienvenido a Vision+ Play! Yo pongo la voz, tú pones las ganas.", R.raw.narrator_welcome_2),
            NarratorVariant("¡Hola, hola! Soy la narradora. Esta noche se juega en serio.", R.raw.narrator_welcome_3),
            NarratorVariant("¡Qué bueno que llegaste! Aquí empieza lo bueno.", R.raw.narrator_welcome_4),
            NarratorVariant("¡Saludos, jugador! Vision+ Play te estaba esperando.", R.raw.narrator_welcome_5),
            NarratorVariant("¡Ey! ¿Listo para jugar? Yo ya calenté la voz.", R.raw.narrator_welcome_6),
            NarratorVariant("¡Bienvenido al plató! Ponte cómodo, que esto se pone bueno.", R.raw.narrator_welcome_7),
            NarratorVariant("¡Hola! Júntense todos, que ya vamos a empezar.", R.raw.narrator_welcome_8),
            NarratorVariant("¡Qué onda! Soy la narradora de Vision+ Play. ¿Jugamos?", R.raw.narrator_welcome_9),
        )
        NarratorEvent.Selector -> listOf(
            NarratorVariant("Elige el juego. Tú pones las ganas y yo le pongo el sabor.", R.raw.narrator_selector_0),
            NarratorVariant("Escoge tu veneno: trivia, charadas o apuestas.", R.raw.narrator_selector_1),
            NarratorVariant("¿Qué se juega hoy? Tú mandas, yo narro.", R.raw.narrator_selector_2),
            NarratorVariant("Tres juegos, una noche. Elige sabiamente.", R.raw.narrator_selector_3),
            NarratorVariant("La carta está servida. ¿Por dónde empezamos?", R.raw.narrator_selector_4),
            NarratorVariant("Elige un juego. Yo pongo la voz y el ritmo.", R.raw.narrator_selector_5),
            NarratorVariant("¿Trivia, actuación o apuestas? Difícil decisión.", R.raw.narrator_selector_6),
            NarratorVariant("Todo listo. Solo falta que elijas el juego.", R.raw.narrator_selector_7),
            NarratorVariant("Mira la carta y dime: ¿qué jugamos primero?", R.raw.narrator_selector_8),
            NarratorVariant("Elige el juego de esta noche. Sin presiones... o con poquitas.", R.raw.narrator_selector_9),
        )
        NarratorEvent.RoomCreated -> listOf(
            NarratorVariant("Sala lista, jefe. Pasa el código antes de que se enfríen los tacos.", R.raw.narrator_create_0),
            NarratorVariant("Sala creada. Comparte el código con tus amigos.", R.raw.narrator_create_1),
            NarratorVariant("La sala ya está abierta. ¡Que se junte la banda!", R.raw.narrator_create_2),
            NarratorVariant("Código listo. Pásalo antes de que se acabe la fiesta.", R.raw.narrator_create_3),
            NarratorVariant("Sala creada. Corre a avisarle a todos.", R.raw.narrator_create_4),
            NarratorVariant("Ya hay sala. El código es tu boleto de entrada.", R.raw.narrator_create_5),
            NarratorVariant("La mesa está puesta. Comparte el código.", R.raw.narrator_create_6),
            NarratorVariant("Sala lista. Que nadie se quede fuera.", R.raw.narrator_create_7),
            NarratorVariant("Código en mano. ¡A juntar jugadores!", R.raw.narrator_create_8),
            NarratorVariant("Sala abierta. Pasa el código por el grupo.", R.raw.narrator_create_9),
        )
        NarratorEvent.Joined -> listOf(
            NarratorVariant("Ya estás dentro. Ponte cómodo, que esto se va a poner bueno.", R.raw.narrator_join_0),
            NarratorVariant("¡Bienvenido a la sala! Agarra tu lugar.", R.raw.narrator_join_1),
            NarratorVariant("Ya entraste. La diversión está por empezar.", R.raw.narrator_join_2),
            NarratorVariant("¡Un jugador más! La sala se pone buena.", R.raw.narrator_join_3),
            NarratorVariant("Dentro. Ponte listo, que ya casi empezamos.", R.raw.narrator_join_4),
            NarratorVariant("¡Hola, nuevo jugador! Bienvenido a la partida.", R.raw.narrator_join_5),
            NarratorVariant("Ya estás en la sala. Que empiece lo bueno.", R.raw.narrator_join_6),
            NarratorVariant("¡Se unió otro valiente! Bienvenido.", R.raw.narrator_join_7),
            NarratorVariant("Entrada confirmada. Acomódate.", R.raw.narrator_join_8),
            NarratorVariant("Ya eres parte del juego. ¡Suerte!", R.raw.narrator_join_9),
        )
        NarratorEvent.Started -> listOf(
            NarratorVariant("¡Que empiece la trivia! Aquí no hay empate, hay revancha.", R.raw.narrator_start_0),
            NarratorVariant("¡Arrancamos! Que gane el más listo.", R.raw.narrator_start_1),
            NarratorVariant("¡Empieza la partida! Concentrados todos.", R.raw.narrator_start_2),
            NarratorVariant("¡Vámonos! La trivia ya empezó.", R.raw.narrator_start_3),
            NarratorVariant("¡Que comience el juego! Sin piedad.", R.raw.narrator_start_4),
            NarratorVariant("¡Arranca la trivia! A demostrar quién sabe más.", R.raw.narrator_start_5),
            NarratorVariant("¡Empezamos! Que nadie se copie.", R.raw.narrator_start_6),
            NarratorVariant("¡La partida ya empezó! Piensen rápido.", R.raw.narrator_start_7),
            NarratorVariant("¡Vámonos recio! Primera pregunta en camino.", R.raw.narrator_start_8),
            NarratorVariant("¡Que empiece lo bueno! Mucha suerte a todos.", R.raw.narrator_start_9),
        )
        NarratorEvent.Correct -> listOf(
            NarratorVariant("¡Chido! Punto para ti.", R.raw.narrator_correct_0),
            NarratorVariant("¡Exacto! Muy bien.", R.raw.narrator_correct_1),
            NarratorVariant("¡Correcto! Vas con todo.", R.raw.narrator_correct_2),
            NarratorVariant("¡Le atinaste! Punto para ti.", R.raw.narrator_correct_3),
            NarratorVariant("¡Eso! Bien jugado.", R.raw.narrator_correct_4),
            NarratorVariant("¡Correctísimo! Sigue así.", R.raw.narrator_correct_5),
            NarratorVariant("¡Buena! Punto para ti.", R.raw.narrator_correct_6),
            NarratorVariant("¡Así se hace! Correcto.", R.raw.narrator_correct_7),
            NarratorVariant("¡Le diste al clavo! Punto.", R.raw.narrator_correct_8),
            NarratorVariant("¡Perfecto! Nadie te para.", R.raw.narrator_correct_9),
        )
        NarratorEvent.Wrong -> listOf(
            NarratorVariant("Casi, casi. Ni modo, a la próxima le atinas.", R.raw.narrator_wrong_0),
            NarratorVariant("¡Uy! Por poquito. A la próxima.", R.raw.narrator_wrong_1),
            NarratorVariant("Casi. La respuesta era otra.", R.raw.narrator_wrong_2),
            NarratorVariant("¡No! Pero buen intento.", R.raw.narrator_wrong_3),
            NarratorVariant("Cerca, pero no. Ni modo.", R.raw.narrator_wrong_4),
            NarratorVariant("¡Fallaste! La próxima será.", R.raw.narrator_wrong_5),
            NarratorVariant("Esa no era. Tranquilo, quedan rondas.", R.raw.narrator_wrong_6),
            NarratorVariant("¡Casi le atinas! Otra vez será.", R.raw.narrator_wrong_7),
            NarratorVariant("No fue esa. A seguir jugando.", R.raw.narrator_wrong_8),
            NarratorVariant("¡Por un pelito! Ni modo.", R.raw.narrator_wrong_9),
        )
        NarratorEvent.TimeUp -> listOf(
            NarratorVariant("¡Se acabó el tiempo! Se nos hizo tarde, como siempre.", R.raw.narrator_time_0),
            NarratorVariant("¡Tiempo! Ni modo, muy lento.", R.raw.narrator_time_1),
            NarratorVariant("¡Se acabó el tiempo! Más rápido a la próxima.", R.raw.narrator_time_2),
            NarratorVariant("¡Tiempo fuera! Se durmieron.", R.raw.narrator_time_3),
            NarratorVariant("¡Ya fue! El tiempo no perdona.", R.raw.narrator_time_4),
            NarratorVariant("¡Se acabó! Respondan más rápido.", R.raw.narrator_time_5),
            NarratorVariant("¡Tiempo! El reloj no espera.", R.raw.narrator_time_6),
            NarratorVariant("¡Fin del tiempo! A ponerse las pilas.", R.raw.narrator_time_7),
            NarratorVariant("¡Se nos fue el tiempo! Ni modo.", R.raw.narrator_time_8),
            NarratorVariant("¡Tiempo agotado! La próxima con más velocidad.", R.raw.narrator_time_9),
        )
        NarratorEvent.Reveal -> listOf(
            NarratorVariant("Esta era la respuesta. No me vean así.", R.raw.narrator_reveal_0),
            NarratorVariant("La respuesta correcta era esta. ¿La tenían?", R.raw.narrator_reveal_1),
            NarratorVariant("Ahí estaba la respuesta. Ni modo.", R.raw.narrator_reveal_2),
            NarratorVariant("Esta era. ¿Quién le atinó?", R.raw.narrator_reveal_3),
            NarratorVariant("La respuesta era esta. Anótenla.", R.raw.narrator_reveal_4),
            NarratorVariant("Pues era esta. ¿Alguien la sacó?", R.raw.narrator_reveal_5),
            NarratorVariant("La correcta era esta. Siguiente ronda.", R.raw.narrator_reveal_6),
            NarratorVariant("Esta era la buena. Ni modo.", R.raw.narrator_reveal_7),
            NarratorVariant("Respuesta revelada. ¿Cómo les fue?", R.raw.narrator_reveal_8),
            NarratorVariant("Era esta. A lo que sigue.", R.raw.narrator_reveal_9),
        )
        NarratorEvent.Podium -> listOf(
            NarratorVariant("¡Tenemos ganador! Invita los tacos, ¿no?", R.raw.narrator_podium_0),
            NarratorVariant("¡Ganador! Felicidades al campeón.", R.raw.narrator_podium_1),
            NarratorVariant("¡Se acabó! El ganador se lleva los honores.", R.raw.narrator_podium_2),
            NarratorVariant("¡Tenemos campeón! Que invite algo.", R.raw.narrator_podium_3),
            NarratorVariant("¡Partida terminada! Felicidades al ganador.", R.raw.narrator_podium_4),
            NarratorVariant("¡Ganó el mejor! Aplausos para el campeón.", R.raw.narrator_podium_5),
            NarratorVariant("¡Fin del juego! El podio ya está decidido.", R.raw.narrator_podium_6),
            NarratorVariant("¡Campeón! A celebrar se ha dicho.", R.raw.narrator_podium_7),
            NarratorVariant("¡Se cerró la partida! Felicidades a todos.", R.raw.narrator_podium_8),
            NarratorVariant("¡El ganador manda! Gracias por jugar.", R.raw.narrator_podium_9),
        )
        NarratorEvent.Solo -> listOf(
            NarratorVariant("Modo solo. Juega a tu ritmo, sin prisas.", R.raw.narrator_solo_0),
            NarratorVariant("Vas solo. Tranquilo, sin presión.", R.raw.narrator_solo_1),
            NarratorVariant("Modo solo. A tu paso.", R.raw.narrator_solo_2),
            NarratorVariant("Juegas solo. Disfruta el ritmo.", R.raw.narrator_solo_3),
            NarratorVariant("Solo tú y la trivia. Sin prisas.", R.raw.narrator_solo_4),
            NarratorVariant("Modo solitario. Tú pones el ritmo.", R.raw.narrator_solo_5),
            NarratorVariant("Vas por tu cuenta. Con calma.", R.raw.narrator_solo_6),
            NarratorVariant("Un jugador, toda la gloria. A tu ritmo.", R.raw.narrator_solo_7),
            NarratorVariant("Modo solo. Sin rivales, sin presión.", R.raw.narrator_solo_8),
            NarratorVariant("Tú contra la trivia. ¡Venga!", R.raw.narrator_solo_9),
        )
        NarratorEvent.CharadaTurn -> listOf(
            NarratorVariant("¡A actuar! El público adivina en voz alta. Sin decir el nombre, ¿eh?", R.raw.narrator_charada_turn_0),
            NarratorVariant("¡Te toca actuar! Nada de decir el título.", R.raw.narrator_charada_turn_1),
            NarratorVariant("¡A la mímica! El público adivina gritando.", R.raw.narrator_charada_turn_2),
            NarratorVariant("¡Actúa ya! Sin palabras prohibidas.", R.raw.narrator_charada_turn_3),
            NarratorVariant("¡Tu turno de actuar! Que se note el talento.", R.raw.narrator_charada_turn_4),
            NarratorVariant("¡A escenificar! El público ayuda gritando.", R.raw.narrator_charada_turn_5),
            NarratorVariant("¡Actuando en tres, dos, uno! Sin decir el nombre.", R.raw.narrator_charada_turn_6),
            NarratorVariant("¡Tú actúas, ellos adivinan! Manos a la obra.", R.raw.narrator_charada_turn_7),
            NarratorVariant("¡A mover las manos! El título no se dice.", R.raw.narrator_charada_turn_8),
            NarratorVariant("¡Escenario tuyo! Actúa sin decir ni pío del nombre.", R.raw.narrator_charada_turn_9),
        )
        NarratorEvent.CharadaCorrect -> listOf(
            NarratorVariant("¡Exacto! Sigue así, que vas como cohete.", R.raw.narrator_charada_correct_0),
            NarratorVariant("¡Le atinaron! Punto para el actor.", R.raw.narrator_charada_correct_1),
            NarratorVariant("¡Correcto! Buen equipo hacen.", R.raw.narrator_charada_correct_2),
            NarratorVariant("¡Adivinada! Sigue actuando.", R.raw.narrator_charada_correct_3),
            NarratorVariant("¡Eso era! Punto bien ganado.", R.raw.narrator_charada_correct_4),
            NarratorVariant("¡Lo sacaron! Vas volando.", R.raw.narrator_charada_correct_5),
            NarratorVariant("¡Bien adivinado! Otro punto.", R.raw.narrator_charada_correct_6),
            NarratorVariant("¡Correcto! El público sí ayuda.", R.raw.narrator_charada_correct_7),
            NarratorVariant("¡Acertaron! Que no pare la racha.", R.raw.narrator_charada_correct_8),
            NarratorVariant("¡Exactamente eso! Punto para ti.", R.raw.narrator_charada_correct_9),
        )
        NarratorEvent.CharadaPass -> listOf(
            NarratorVariant("Pasamos. ¡Siguiente! No se duerman.", R.raw.narrator_charada_pass_0),
            NarratorVariant("¡Pase! Vamos con otra.", R.raw.narrator_charada_pass_1),
            NarratorVariant("Se pasa. Siguiente título.", R.raw.narrator_charada_pass_2),
            NarratorVariant("¡Otra! Esa estaba difícil.", R.raw.narrator_charada_pass_3),
            NarratorVariant("Pasamos esa. ¡La que sigue!", R.raw.narrator_charada_pass_4),
            NarratorVariant("¡Siguiente! Sin atorarse.", R.raw.narrator_charada_pass_5),
            NarratorVariant("Pase concedido. Otra carta.", R.raw.narrator_charada_pass_6),
            NarratorVariant("Esa no salió. ¡Vamos con otra!", R.raw.narrator_charada_pass_7),
            NarratorVariant("¡Se pasa! Mejor la siguiente.", R.raw.narrator_charada_pass_8),
            NarratorVariant("Pase. No se claven, sigue el juego.", R.raw.narrator_charada_pass_9),
        )
        NarratorEvent.CharadaReveal -> listOf(
            NarratorVariant("Se acabó el turno. Buen trabajo, aunque sea de milagro.", R.raw.narrator_charada_reveal_0),
            NarratorVariant("¡Fin del turno! Buen esfuerzo.", R.raw.narrator_charada_reveal_1),
            NarratorVariant("Se acabó el tiempo de actuar. Bien jugado.", R.raw.narrator_charada_reveal_2),
            NarratorVariant("¡Turno terminado! Aplausos al actor.", R.raw.narrator_charada_reveal_3),
            NarratorVariant("Fin del turno. Descansa esas manos.", R.raw.narrator_charada_reveal_4),
            NarratorVariant("¡Se acabó! El actor lo dio todo.", R.raw.narrator_charada_reveal_5),
            NarratorVariant("Turno completo. Buena actuación.", R.raw.narrator_charada_reveal_6),
            NarratorVariant("¡Corte! Se acabó el turno.", R.raw.narrator_charada_reveal_7),
            NarratorVariant("Fin de la actuación. ¡Bravo!", R.raw.narrator_charada_reveal_8),
            NarratorVariant("El turno terminó. A calificar al actor.", R.raw.narrator_charada_reveal_9),
        )
        NarratorEvent.WagerStart -> listOf(
            NarratorVariant("Hagan sus apuestas. La primera pista vale el triple, así que piénsenle.", R.raw.narrator_wager_start_0),
            NarratorVariant("¡A apostar! La primera pista paga triple.", R.raw.narrator_wager_start_1),
            NarratorVariant("Apuestas abiertas. Temprano vale más.", R.raw.narrator_wager_start_2),
            NarratorVariant("¿Cuánto le meten? La primera pista paga el triple.", R.raw.narrator_wager_start_3),
            NarratorVariant("Hagan sus apuestas. El que arriesga, gana.", R.raw.narrator_wager_start_4),
            NarratorVariant("¡Apuesten ya! Primera pista, triple premio.", R.raw.narrator_wager_start_5),
            NarratorVariant("La mesa está abierta. Apuesta con cabeza.", R.raw.narrator_wager_start_6),
            NarratorVariant("¿Cuánto vale tu instinto? Apuesta ahora.", R.raw.narrator_wager_start_7),
            NarratorVariant("Apuestas, apuestas. La primera pista es oro.", R.raw.narrator_wager_start_8),
            NarratorVariant("Pongan sus puntos sobre la mesa. ¡Ya!", R.raw.narrator_wager_start_9),
        )
        NarratorEvent.WagerClue -> listOf(
            NarratorVariant("Una pista más. Si fallan, pierden la apuesta… y la dignidad.", R.raw.narrator_wager_clue_0),
            NarratorVariant("Otra pista. Pero vale menos, ¿eh?", R.raw.narrator_wager_clue_1),
            NarratorVariant("Pista nueva. El premio va bajando.", R.raw.narrator_wager_clue_2),
            NarratorVariant("Ahí va otra pista. Piensen rápido.", R.raw.narrator_wager_clue_3),
            NarratorVariant("Una más. Cada pista vale menos.", R.raw.narrator_wager_clue_4),
            NarratorVariant("Nueva pista en camino. El triple ya se fue.", R.raw.narrator_wager_clue_5),
            NarratorVariant("Otra pistita. Ya no paga triple.", R.raw.narrator_wager_clue_6),
            NarratorVariant("Segunda oportunidad con pista nueva.", R.raw.narrator_wager_clue_7),
            NarratorVariant("Llega otra pista. ¿Ahora sí?", R.raw.narrator_wager_clue_8),
            NarratorVariant("Una pista más. El reloj no perdona.", R.raw.narrator_wager_clue_9),
        )
        NarratorEvent.WagerReveal -> listOf(
            NarratorVariant("La respuesta era… ¡a ver quién se llevó la apuesta!", R.raw.narrator_wager_reveal_0),
            NarratorVariant("¡Se revela la respuesta! ¿Quién ganó?", R.raw.narrator_wager_reveal_1),
            NarratorVariant("La respuesta era esta. Revisen sus apuestas.", R.raw.narrator_wager_reveal_2),
            NarratorVariant("Ahí está la respuesta. ¡A ver los ganadores!", R.raw.narrator_wager_reveal_3),
            NarratorVariant("Respuesta revelada. ¿Quién se hizo rico?", R.raw.narrator_wager_reveal_4),
            NarratorVariant("Era esta. ¿Acertaron su apuesta?", R.raw.narrator_wager_reveal_5),
            NarratorVariant("La respuesta correcta… ¿Quién cobra?", R.raw.narrator_wager_reveal_6),
            NarratorVariant("¡Destapada la respuesta! Cuentas claras.", R.raw.narrator_wager_reveal_7),
            NarratorVariant("Esta era. Vamos a ver quién ganó.", R.raw.narrator_wager_reveal_8),
            NarratorVariant("Respuesta en mesa. ¿Quién apostó bien?", R.raw.narrator_wager_reveal_9),
        )
        NarratorEvent.WagerWin -> listOf(
            NarratorVariant("¡Apuesta ganada! Puntos para ti. Invita algo.", R.raw.narrator_wager_win_0),
            NarratorVariant("¡Ganaste la apuesta! Buen olfato.", R.raw.narrator_wager_win_1),
            NarratorVariant("¡Cobrando! Apuesta ganada.", R.raw.narrator_wager_win_2),
            NarratorVariant("¡Lo lograste! La apuesta es tuya.", R.raw.narrator_wager_win_3),
            NarratorVariant("¡Ganador de la apuesta! Felicidades.", R.raw.narrator_wager_win_4),
            NarratorVariant("¡Bien apostado! Puntos para ti.", R.raw.narrator_wager_win_5),
            NarratorVariant("¡La apuesta cayó! A celebrar.", R.raw.narrator_wager_win_6),
            NarratorVariant("¡Ganaste! Buen instinto.", R.raw.narrator_wager_win_7),
            NarratorVariant("¡Apuesta ganada! El riesgo valió.", R.raw.narrator_wager_win_8),
            NarratorVariant("¡Cobra tus puntos! Bien jugado.", R.raw.narrator_wager_win_9),
        )
        NarratorEvent.WagerLose -> listOf(
            NarratorVariant("La apuesta se fue. La próxima será, no se achicopalen.", R.raw.narrator_wager_lose_0),
            NarratorVariant("¡Perdiste la apuesta! Ni modo.", R.raw.narrator_wager_lose_1),
            NarratorVariant("Se fue la apuesta. A la próxima.", R.raw.narrator_wager_lose_2),
            NarratorVariant("¡La apuesta se perdió! Tranquilo.", R.raw.narrator_wager_lose_3),
            NarratorVariant("Ni modo, la apuesta voló. Otra vez será.", R.raw.narrator_wager_lose_4),
            NarratorVariant("Apuesta perdida. No se agüiten.", R.raw.narrator_wager_lose_5),
            NarratorVariant("¡Falló la apuesta! La suerte cambia.", R.raw.narrator_wager_lose_6),
            NarratorVariant("Se perdieron esos puntos. Ni modo.", R.raw.narrator_wager_lose_7),
            NarratorVariant("La apuesta no cuajó. Sigue jugando.", R.raw.narrator_wager_lose_8),
            NarratorVariant("¡Adiós apuesta! La próxima la ganas.", R.raw.narrator_wager_lose_9),
        )
        NarratorEvent.ImpostorSecret -> listOf(
            NarratorVariant("Teléfonos arriba. Uno de ustedes no sabe dónde está.", R.raw.narrator_impostor_secret_0),
            NarratorVariant("Reparto secreto. El impostor ya está temblando.", R.raw.narrator_impostor_secret_1),
            NarratorVariant("Miren su pantalla y disimulen. Aquí empieza la mentira.", R.raw.narrator_impostor_secret_2),
        )
        NarratorEvent.ImpostorVote -> listOf(
            NarratorVariant("¿Quién miente? Señalen sin piedad.", R.raw.narrator_impostor_vote_0),
            NarratorVariant("Hora de votar. Las caras no mienten… o sí.", R.raw.narrator_impostor_vote_1),
            NarratorVariant("Voten al impostor. Si le atinan, se pone bueno.", R.raw.narrator_impostor_vote_2),
        )
        NarratorEvent.ImpostorCaught -> listOf(
            NarratorVariant("¡Lo atraparon! Pero todavía puede robar la ronda.", R.raw.narrator_impostor_caught_0),
            NarratorVariant("Descubierto. Ahora tiene una última oportunidad.", R.raw.narrator_impostor_caught_1),
            NarratorVariant("¡Cayó el impostor! A ver si adivina el lugar.", R.raw.narrator_impostor_caught_2),
        )
        NarratorEvent.DrawStart -> listOf(
            NarratorVariant("A dibujar. Sin letras, sin trampas y sin pena.", R.raw.narrator_draw_start_0),
            NarratorVariant("El lienzo está listo. Que empiece la obra maestra.", R.raw.narrator_draw_start_1),
            NarratorVariant("Dibuja rápido, que la tele ya está mirando.", R.raw.narrator_draw_start_2),
        )
        NarratorEvent.DrawVote -> listOf(
            NarratorVariant("¿Cuál es el título real? Voten, mentirosos.", R.raw.narrator_draw_vote_0),
            NarratorVariant("Hora de votar. Busquen el título real entre tanta mentira.", R.raw.narrator_draw_vote_1),
            NarratorVariant("Elijan el título verdadero. Sin trampas.", R.raw.narrator_draw_vote_2),
        )
        NarratorEvent.AuctionStart -> listOf(
            NarratorVariant("Se abre la subasta. Saquen las carteras y los faroles.", R.raw.narrator_auction_start_0),
            NarratorVariant("Lote en vitrina. Pujen por debajo del agua, sin que los vean.", R.raw.narrator_auction_start_1),
            NarratorVariant("El mercado negro abre. Que gane el más valiente.", R.raw.narrator_auction_start_2),
        )
        NarratorEvent.AuctionReveal -> listOf(
            NarratorVariant("¡Martillo! Veamos quién se quedó con el botín.", R.raw.narrator_auction_reveal_0),
            NarratorVariant("Se cierra la puja. Los empates se cancelan.", R.raw.narrator_auction_reveal_1),
            NarratorVariant("El martillo habló. A pagar o a llorar.", R.raw.narrator_auction_reveal_2),
        )
        NarratorEvent.FibbageWrite -> listOf(
            NarratorVariant("Inventen una mentira que suene a verdad. Aquí nadie sabe nada.", R.raw.narrator_fibbage_write_0),
            NarratorVariant("Si no sabes, invéntalo con cara de experto.", R.raw.narrator_fibbage_write_1),
            NarratorVariant("La verdad está escondida. Ustedes inventen el resto.", R.raw.narrator_fibbage_write_2),
        )
        NarratorEvent.FibbageVote -> listOf(
            NarratorVariant("Voten la que crean verdadera. Alguien va a morder.", R.raw.narrator_fibbage_vote_0),
            NarratorVariant("Hora de votar. La mentira más creíble se lleva los puntos.", R.raw.narrator_fibbage_vote_1),
            NarratorVariant("Señalen la verdad. Si caen, se ríen y suman.", R.raw.narrator_fibbage_vote_2),
        )
        NarratorEvent.ImpostorQuestions -> listOf(
            NarratorVariant("Empieza el interrogatorio. Hagan sus preguntas en voz alta y fíjense quién suda.", R.raw.narrator_impostor_questions_0),
            NarratorVariant("A interrogar. Respondan rápido… el que duda, se delata solo.", R.raw.narrator_impostor_questions_1),
            NarratorVariant("Micrófono abierto en la sala. Cuidado con dar demasiadas pistas al impostor.", R.raw.narrator_impostor_questions_2),
        )
        NarratorEvent.ImpostorEscaped -> listOf(
            NarratorVariant("¡Se les escapó en la cara! El impostor se lleva el botín.", R.raw.narrator_impostor_escaped_0),
            NarratorVariant("Les vieron la cara a todos. Victoria limpia para el impostor.", R.raw.narrator_impostor_escaped_1),
            NarratorVariant("Ni Sherlock los salvaba hoy. El impostor salió caminando por la puerta grande.", R.raw.narrator_impostor_escaped_2),
        )
        NarratorEvent.PartyTimeout -> listOf(
            NarratorVariant("¡Tiempo! El que no mandó nada, se quedó mirando.", R.raw.narrator_party_timeout_0),
            NarratorVariant("Reloj en cero. A jugar con lo que alcanzaron a mandar.", R.raw.narrator_party_timeout_1),
            NarratorVariant("Se acabó el tiempo. Más rápidos los dedos para la próxima.", R.raw.narrator_party_timeout_2),
        )
        NarratorEvent.AuctionTie -> listOf(
            NarratorVariant("¡Choque de ambición! Empate en la cima, esas pujas quedan anuladas.", R.raw.narrator_auction_tie_0),
            NarratorVariant("Pensaron igualito y se quedaron sin nada. Las pujas empatadas se cancelan.", R.raw.narrator_auction_tie_1),
            NarratorVariant("Doble codicia, cero premio. El empate arriba le regala el lote al que sigue.", R.raw.narrator_auction_tie_2),
        )
        NarratorEvent.LieSweep -> listOf(
            NarratorVariant("¡Cayeron redonditos! Esa mentira se acaba de llevar a media sala.", R.raw.narrator_lie_sweep_0),
            NarratorVariant("Maestro del engaño en la sala. Todos mordieron el mismo anzuelo.", R.raw.narrator_lie_sweep_1),
            NarratorVariant("Qué manera de vender humo… y qué manera de comprarlo todos juntos.", R.raw.narrator_lie_sweep_2),
        )
        NarratorEvent.NobodyGuessed -> listOf(
            NarratorVariant("Nadie le atinó a la verdad. O el dibujo era arte abstracto, o mienten demasiado bien.", R.raw.narrator_nobody_guessed_0),
            NarratorVariant("Cero aciertos a la respuesta real. La verdad quedó completamente enterrada.", R.raw.narrator_nobody_guessed_1),
            NarratorVariant("Ni uno solo dio en el blanco. Qué desastre más hermoso.", R.raw.narrator_nobody_guessed_2),
        )
        NarratorEvent.FinalRoundDouble -> listOf(
            NarratorVariant("Última ronda y puntos al doble. Aquí se define quién manda y quién lava los platos.", R.raw.narrator_final_round_double_0),
            NarratorVariant("Ronda final. Todo vale el doble, así que todavía pueden arruinarle la noche al líder.", R.raw.narrator_final_round_double_1),
            NarratorVariant("Última llamada. Con el doble de puntos en juego, nadie tiene la corona segura.", R.raw.narrator_final_round_double_2),
        )
    }

    /** Elige una variante distinta a la anterior para no sonar repetitivo. */
    fun pickVariant(
        event: NarratorEvent,
        lastIndex: Int,
        random: kotlin.random.Random = kotlin.random.Random,
    ): Int {
        val count = variantsFor(event).size
        if (count <= 1) return 0
        val drawn = random.nextInt(count)
        return if (drawn == lastIndex) (drawn + 1) % count else drawn
    }

    private val cueCache = mutableMapOf<String, Int>()
    private val lastPerEvent = mutableMapOf<NarratorEvent, Int>()

    /**
     * La variante para un momento del juego. La misma [key] devuelve siempre la
     * misma variante (así el bocadillo y la voz dicen lo mismo); una key nueva
     * elige una distinta a la anterior del mismo evento.
     */
    fun cue(event: NarratorEvent, key: String): NarratorVariant {
        val cacheKey = "${variantId(event)}|$key"
        val index = cueCache.getOrPut(cacheKey) {
            pickVariant(event, lastPerEvent[event] ?: -1).also { lastPerEvent[event] = it }
        }
        return variantsFor(event)[index]
    }

    fun resetCuesForTests() {
        cueCache.clear()
        lastPerEvent.clear()
    }

    fun frames(): IntArray = intArrayOf(
        R.drawable.narrator_idle_0,
        R.drawable.narrator_idle_1,
        R.drawable.narrator_idle_2,
        R.drawable.narrator_idle_3,
    )

    fun staticFrame(): Int = R.drawable.narrator_static

    /** La pose del presentador para cada momento (generadas con ComfyUI local). */
    fun moodFrame(mood: NarratorMood): Int = when (mood) {
        NarratorMood.Idle -> R.drawable.narrator_static
        NarratorMood.Welcome -> R.drawable.narrator_welcome
        NarratorMood.Thinking -> R.drawable.narrator_thinking
        NarratorMood.Happy -> R.drawable.narrator_happy
        NarratorMood.Oops -> R.drawable.narrator_oops
        NarratorMood.Celebrate -> R.drawable.narrator_celebrate
    }

    fun avatar(): Int = R.drawable.narrator_avatar

    fun revealEvent(answered: Boolean, correct: Boolean): NarratorEvent = when {
        !answered -> NarratorEvent.TimeUp
        correct -> NarratorEvent.Correct
        else -> NarratorEvent.Wrong
    }

    fun phaseEvent(phase: String): NarratorEvent? = when (phase) {
        "countdown" -> NarratorEvent.Started
        "turn" -> NarratorEvent.CharadaTurn
        "wager" -> NarratorEvent.WagerStart
        "clue" -> NarratorEvent.WagerClue
        "podium" -> NarratorEvent.Podium
        else -> null
    }

    /** Línea del presentador según el juego y el momento, sin repetir la misma frase. */
    fun hostLine(game: String, phase: String, actor: String = "", correct: Int = 0): String {
        return when (phase) {
            "lobby" -> "Sala lista. Comparte el código."
            "countdown" -> "¡Que empiece ${if (game == "charadas") "la función" else "la partida"}!"
            "turn" -> if (actor.isNotBlank()) "Turno de $actor. ¡A actuar!" else "¡A actuar!"
            "wager" -> "Hagan sus apuestas. La primera pista vale el triple."
            "clue" -> "Una pista más. Si fallas, pierdes la apuesta."
            "reveal" -> if (game == "charadas" && correct > 0) {
                "¡$correct ${if (correct == 1) "acierto" else "aciertos"} en el turno!"
            } else {
                "Esta era la respuesta."
            }
            "podium" -> "¡Tenemos ganador!"
            "closed" -> "La sala se cerró."
            else -> variantsFor(NarratorEvent.Welcome).first().text
        }
    }
}
