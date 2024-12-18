package com.example.battleship

import androidx.compose.foundation.Image
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.TextFieldDefaults

@Composable
fun Navigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "MainScreen") {
        composable("MainScreen") { MainScreen(navController) }
        composable("LobbyScreen/{username}") { backStackEntry ->
            val loggedInUsername = backStackEntry.arguments?.getString("username").orEmpty()
            if (loggedInUsername.isBlank()) {
                Toast.makeText(LocalContext.current, "Invalid username", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            } else {
                LobbyScreen(navController, loggedInUsername)
            }
        }
        composable("GameBoardScreen?playerName={playerName}&opponentName={opponentName}") { backStackEntry ->
            val playerName = backStackEntry.arguments?.getString("playerName").orEmpty()
            val opponentName = backStackEntry.arguments?.getString("opponentName").orEmpty()
            GameBoardScreen(navController, playerName, opponentName)
        }
    }
}

@Composable
fun Background(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.pxfuel),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController) {
    var username by remember { mutableStateOf(TextFieldValue("")) }
    val firestore = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    Background {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)) // Semi-transparent overlay
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Welcome to Battleship",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White, // White text for better contrast
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Enter Username", color = Color.White) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.LightGray,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White,
                        cursorColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Button(
                    onClick = {
                        if (username.text.isNotBlank()) {
                            checkAndAddPlayer(
                                username = username.text.trim(),
                                firestore = firestore,
                                onPlayerExists = {
                                    Toast.makeText(
                                        context,
                                        "Welcome back, ${username.text.trim()}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    navController.navigate("LobbyScreen/${username.text.trim()}")
                                },
                                onPlayerAdded = {
                                    Toast.makeText(
                                        context,
                                        "Player added successfully!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    navController.navigate("LobbyScreen/${username.text.trim()}")
                                },
                                onError = { errorMessage ->
                                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            Toast.makeText(context, "Please enter a valid Username", Toast.LENGTH_SHORT)
                                .show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.DarkGray,
                        contentColor = Color.White
                    )
                ) {
                    Text("Join Lobby", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

//Helper function
fun checkAndAddPlayer(
    username: String,
    firestore: FirebaseFirestore,
    onPlayerExists: () -> Unit,
    onPlayerAdded: () -> Unit,
    onError: (String) -> Unit
) {
    val playersCollection = firestore.collection("players")

    playersCollection
        .whereEqualTo("name", username)
        .get()
        .addOnSuccessListener { snapshot ->
            if (!snapshot.isEmpty) {
                playersCollection
                    .document(snapshot.documents[0].id)
                    .update("status", "online")
                    .addOnSuccessListener { onPlayerExists() }
                    .addOnFailureListener { onError("Error updating player status") }
            } else {
                playersCollection
                    .add(
                        mapOf(
                            "name" to username,
                            "status" to "online",
                            "playerId" to UUID.randomUUID().toString()
                        )
                    )
                    .addOnSuccessListener { onPlayerAdded() }
                    .addOnFailureListener { exception ->
                        onError("Error adding player: ${exception.message}")
                    }
            }
        }
        .addOnFailureListener { exception ->
            onError("Error checking player: ${exception.message}")
        }
}

//Helper function
private fun setPlayerStatus(username: String, status: String, firestore: FirebaseFirestore) {
    firestore.collection("players")
        .whereEqualTo("name", username)
        .get()
        .addOnSuccessListener { snapshot ->
            if (!snapshot.isEmpty) {
                snapshot.documents[0].reference.update("status", status)
                    .addOnSuccessListener {
                    }
                    .addOnFailureListener { error ->
                        Log.e("Error", "Error updating player status: ${error.message}")
                    }
            } else {
                Log.d("Error", "Player document not found for username: $username")
            }
        }
        .addOnFailureListener { error ->
            Log.e("Error", "Error fetching player document: ${error.message}")
        }
}

//Helper function
@Composable
fun PlayerRow(player: String, onChallengeClick: () -> Unit) {
    // Split the player string into name and status
    val parts = player.split(" - ")
    val playerName = parts.getOrNull(0) ?: ""
    val status = parts.getOrNull(1) ?: ""

    val statusColor = if (status.equals("online", ignoreCase = true)) Color.Green else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            // Add a semi-transparent overlay for the row
            .background(Color.Black.copy(alpha = 0.4f), shape = MaterialTheme.shapes.small)
            .border(1.dp, Color.White, shape = MaterialTheme.shapes.small)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = playerName, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Text(text = status, fontSize = 14.sp, color = statusColor)
        }
        Button(
            onClick = {
                Log.d("Challenge", "Challenge button clicked for $playerName")
                onChallengeClick()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White
            )
        ) {
            Text("Challenge")
        }
    }
}


@Composable
fun GameInvitation(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogTitle: String,
    dialogText: String,
    icon: ImageVector,
) {
    AlertDialog(
        icon = {
            Icon(icon, contentDescription = "Game Invitation Alert")
        },
        title = {
            Text(text = dialogTitle)
        },
        text = {
            Text(text = dialogText)
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmation()
                }
            ) {
                Text("Accept")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text("Decline")
            }
        }
    )
}

@Composable
fun LobbyScreen(navController: NavController, loggedInUsername: String) {
    val players = remember { mutableStateListOf<String>() }
    val firestore = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var opponent by remember { mutableStateOf<String?>(null) }
    var incomingChallenge by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    // New state variable to store the challenge ID created by this player
    var outgoingChallengeId by remember { mutableStateOf<String?>(null) }
    var outgoingChallengeOpponent by remember { mutableStateOf<String?>(null) }

    // Lifecycle observer for setting player online/offline
    LaunchedEffect(lifecycleOwner) {
        val observer = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        setPlayerStatus(loggedInUsername, "online", firestore)
                    }

                    Lifecycle.Event.ON_STOP -> {
                        setPlayerStatus(loggedInUsername, "offline", firestore)
                    }

                    else -> Unit
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    // Fetch players
    LaunchedEffect(Unit) {
        firestore.collection("players")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(
                        context,
                        "Error fetching players: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addSnapshotListener
                }
                snapshot?.documents?.mapNotNull {
                    val name = it.getString("name")
                    val status = it.getString("status")
                    if (name != null && status != null && name != loggedInUsername) "$name - $status" else null
                }?.let {
                    players.clear()
                    players.addAll(it)
                }
            }
    }

    // When opponent is chosen to challenge, send the challenge
    LaunchedEffect(opponent) {
        opponent?.let { currentOpponent ->
            sendChallenge(
                fromPlayer = loggedInUsername,
                toPlayer = currentOpponent,
                firestore = firestore,
                onSuccess = { challengeId ->
                    Log.d("Challenge", "Challenge sent to $currentOpponent with ID: $challengeId")
                    outgoingChallengeId = challengeId
                    outgoingChallengeOpponent = currentOpponent
                    opponent = null

                    // Listen to changes for this challenge (for the challenger)
                    challengeId?.let { id ->
                        firestore.collection("challenges").document(id)
                            .addSnapshotListener { docSnapshot, error ->
                                if (error != null) {
                                    Log.e("ChallengeError", "Error listening to challenge: ${error.message}")
                                    return@addSnapshotListener
                                }

                                val challengeData = docSnapshot?.data
                                val status = challengeData?.get("status") as? String
                                val fromPlayer = challengeData?.get("fromPlayer") as? String
                                val toPlayer = challengeData?.get("toPlayer") as? String

                                // If this challenge was accepted and this user was the one who sent it, navigate them too
                                if (status == "accepted" && fromPlayer == loggedInUsername && toPlayer == outgoingChallengeOpponent) {
                                    navController.navigate("GameBoardScreen?playerName=$fromPlayer&opponentName=$toPlayer")
                                }
                            }
                    }

                },
                onFailure = { exception ->
                    Log.e("ChallengeError", "Failed to send challenge: ${exception.message}")
                }
            )
        }
    }

    // Listen for incoming challenges
    LaunchedEffect(Unit) {
        listenForChallenges(
            loggedInUsername = loggedInUsername,
            firestore = firestore,
            onIncomingChallenge = { id, fromPlayer, toPlayer ->
                incomingChallenge = Triple(id, fromPlayer, toPlayer)
            }
        )
    }

    // Handle incoming challenge dialog
    incomingChallenge?.let { (challengeId, challenger, _) ->
        GameInvitation(
            onDismissRequest = {
                declineChallenge(challengeId, firestore) // Decline
                incomingChallenge = null
            },
            onConfirmation = {
                acceptChallenge(
                    challengeId = challengeId,
                    fromPlayer = challenger,
                    toPlayer = loggedInUsername,
                    firestore = firestore,
                    navController = navController
                )
                incomingChallenge = null
            },
            dialogTitle = "Game Invitation",
            dialogText = "$challenger has challenged you to a game. Do you accept?",
            icon = Icons.Default.Notifications
        )
    }

    Background {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Lobby",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Text(
                text = "Welcome, $loggedInUsername!",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Text(
                text = "Online Players",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(players) { player ->
                        PlayerRow(player = player) {
                            val selectedOpponent = player.substringBefore(" - ")
                            opponent = selectedOpponent
                        }
                    }
                }
            }
        }

        // Still handle incomingChallenge dialog if needed
        incomingChallenge?.let { (challengeId, challenger, _) ->
            GameInvitation(
                onDismissRequest = {
                    declineChallenge(challengeId, firestore)
                    incomingChallenge = null
                },
                onConfirmation = {
                    acceptChallenge(
                        challengeId = challengeId,
                        fromPlayer = challenger,
                        toPlayer = loggedInUsername,
                        firestore = firestore,
                        navController = navController
                    )
                    incomingChallenge = null
                },
                dialogTitle = "Game Invitation",
                dialogText = "$challenger has challenged you to a game. Do you accept?",
                icon = Icons.Default.Notifications
            )
        }
    }
}

// Adjust sendChallenge to return the challenge ID
fun sendChallenge(
    fromPlayer: String,
    toPlayer: String,
    firestore: FirebaseFirestore,
    onSuccess: (challengeId: String?) -> Unit,
    onFailure: (Exception) -> Unit
) {
    val challenge = mapOf(
        "fromPlayer" to fromPlayer,
        "toPlayer" to toPlayer,
        "status" to "pending"
    )

    firestore.collection("challenges")
        .add(challenge)
        .addOnSuccessListener { docRef ->
            onSuccess(docRef.id)
        }
        .addOnFailureListener { exception -> onFailure(exception) }
}

//Listen for Challenges
fun listenForChallenges(
    loggedInUsername: String,
    firestore: FirebaseFirestore,
    onIncomingChallenge: (String, String, String) -> Unit
) {
    firestore.collection("challenges")
        .whereEqualTo("toPlayer", loggedInUsername) // Filter for challenges targeting the current player
        .whereEqualTo("status", "pending") // Only listen for pending challenges
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("ChallengeError", "Error listening to challenges: ${error.message}")
                return@addSnapshotListener
            }

            // Map documents to challenge data
            snapshot?.documents?.mapNotNull { doc ->
                val id = doc.id
                val fromPlayer = doc.getString("fromPlayer")
                val toPlayer = doc.getString("toPlayer")
                val status = doc.getString("status")

                if (fromPlayer != null && toPlayer != null && status == "pending") {
                    Triple(id, fromPlayer, toPlayer)
                } else null
            }?.firstOrNull()?.let { (id, fromPlayer, toPlayer) ->
                // Pass the first pending challenge to the callback
                onIncomingChallenge(id, fromPlayer, toPlayer)
            }
        }
}


//Accept Challenge
fun acceptChallenge(
    challengeId: String,
    fromPlayer: String,
    toPlayer: String,
    firestore: FirebaseFirestore,
    navController: NavController
) {
    firestore.collection("challenges").document(challengeId)
        .update("status", "accepted")
        .addOnSuccessListener {
            navController.navigate("GameBoardScreen?playerName=$toPlayer&opponentName=$fromPlayer")
        }
        .addOnFailureListener { exception ->
            Log.e("ChallengeError", "Error accepting challenge: ${exception.message}")
        }
}

//Decline Challenge
fun declineChallenge(challengeId: String, firestore: FirebaseFirestore) {
    firestore.collection("challenges").document(challengeId)
        .update("status", "declined")
        .addOnSuccessListener {
            Log.d("Challenge", "Challenge declined successfully")
        }
        .addOnFailureListener { exception ->
            Log.e("ChallengeError", "Error declining challenge: ${exception.message}")
        }
}

//sammys parts
@Composable
fun GameBoardScreen(
    navController: NavController,
    playerName: String,
    opponentName: String
) {
    // Initial variables
    val boardSize = 10
    val playerGrid = remember { MutableList(boardSize) { MutableList(boardSize) { "W" } } }
    val opponentGrid = remember { MutableList(boardSize) { MutableList(boardSize) { "W" } } }
    var gameWon by remember { mutableStateOf(false) }
    var isShipPlacementPhase by remember { mutableStateOf(true) }
    var currentShipIndex by remember { mutableStateOf(0) }
    var isPlayerReady by remember { mutableStateOf(false) }
    var startPoint by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var endPoint by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var isPlayerOneTurn by remember {mutableStateOf(true)} // Track the current player's turn

    val ships = listOf(
        "Carrier" to 4,
        "Battleship" to 3,
        "Cruiser1" to 2,
        "Cruiser2" to 2,
        "Submarine" to 1,
        "Destroyer" to 1
    )

    fun checkGameState(grid: List<List<String>>): Boolean {
        return grid.flatten().none { it == "S" } // No ships left
    }

    fun isWaterAroundCell(row: Int, col: Int): Boolean {
        return if (row in 0 until boardSize && col in 0 until boardSize) {
            playerGrid[row][col] == "W" // Check if the cell is water
        } else {
            true // If out of bounds, assume it's water (no ship can be placed outside the grid)
        }
    }

    fun isWaterAroundShip(cells: List<Pair<Int, Int>>): Boolean {
        for ((r, c) in cells) {
            // Check adjacent cells (top, bottom, left, right)
            if (!isWaterAroundCell(r - 1, c) || // Above
                !isWaterAroundCell(r + 1, c) || // Below
                !isWaterAroundCell(r, c - 1) || // Left
                !isWaterAroundCell(r, c + 1)) { // Right
                return false
            }
        }
        return true
    }



    fun placeShip(startPoint: Pair<Int, Int>, endPoint: Pair<Int, Int>, shipSize: Int): Boolean {
        val (startRow, startCol) = startPoint
        val (endRow, endCol) = endPoint

        // If the ship size is 1, we don't need to check horizontal or vertical.
        // Just check if the start and end points are the same for size 1 ships.
        if (shipSize == 1) {
            if (playerGrid[startRow][startCol] == "W" &&
                isWaterAroundCell(startRow, startCol)) {
                playerGrid[startRow][startCol] = "S" // Place the size 1 ship
                return true
            }
            return false
        }

        // Logic to check if placement is valid (horizontal or vertical)
        val isHorizontal = startRow == endRow
        val isVertical = startCol == endCol

        if (!isHorizontal && !isVertical) return false

        val cells = if (isHorizontal) {
            (minOf(startCol, endCol)..maxOf(startCol, endCol)).map { startRow to it }
        } else {
            (minOf(startRow, endRow)..maxOf(startRow, endRow)).map { it to startCol }
        }

        // Ensure the ship size matches and no overlap
        if (cells.size != shipSize || cells.any { (r, c) -> playerGrid[r][c] != "W" }) {
            return false
        }

        // Check if there's water around the ship placement
        if (!isWaterAroundShip(cells)) {
            return false
        }

        // Place the ship on the grid
        cells.forEach { (r, c) -> playerGrid[r][c] = "S" }
        return true
    }

    fun handleCellClick(row: Int, col: Int) {
        if (isShipPlacementPhase) {
            if (startPoint == null) {
                startPoint = row to col
            } else {
                endPoint = row to col
                val (currentShip, currentSize) = ships[currentShipIndex]

                // Try placing the ship
                if (startPoint != null && endPoint != null) {
                    if (placeShip(startPoint!!, endPoint!!, currentSize)) {
                        if (currentShipIndex < ships.size - 1) {
                            currentShipIndex++
                            startPoint = null
                            endPoint = null
                        } else {
                            isShipPlacementPhase = false // End ship placement phase
                        }
                    } else {
                        startPoint = null
                        endPoint = null // Reset start and end if placement fails
                    }
                }
            }
        } else {
            if (isPlayerOneTurn) {
                // Handle attacks on the opponent's grid
                when (opponentGrid[row][col]) {
                    "W" -> {
                        opponentGrid[row][col] = "M" // Mark as miss
                    }
                    "S" -> {
                        opponentGrid[row][col] = "H" // Mark as hit

                        // Check if all ships are destroyed
                        if (checkGameState(opponentGrid)) {
                            gameWon = true
                        }
                    }
                }
                isPlayerOneTurn = false // Switch to opponent's turn
            } else {
                // Handle opponent's turn (future logic can be added here)
                isPlayerOneTurn = true // Switch back to player one's turn
            }
        }
    }

    @Composable
    fun RenderOpponentGrid(opponentGrid: List<List<String>>, onCellClick: (Int, Int) -> Unit) {
        GameGridView(
            grid = opponentGrid.map { row ->
                row.map { cell -> if (cell == "S") "W" else cell }
            },
            onCellClick = onCellClick
        )
    }

    // UI components and layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(top = 50.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isShipPlacementPhase) {
            val (currentShip, currentSize) = ships[currentShipIndex]

            Text(
                text = "Place Your Ships",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Current Ship: $currentShip ($currentSize spaces)",
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = if (startPoint == null) "Select the starting point" else "Select the ending point",
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Player's Grid for ship placement
            GameGridView(
                grid = playerGrid,
                onCellClick = { row, col -> handleCellClick(row, col) }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Ready Button
        if (!isShipPlacementPhase && !isPlayerReady) {
            Button(
                onClick = { isPlayerReady = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.DarkGray,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxWidth()
            ) {
                Text("Ready")
            }
        }

        // Gameplay Phase
        if (isPlayerReady) {
            Text(
                text = "Game On! Take your turns.",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Player's Grid (with ships) - at the top
            Text(
                text = "Your Ships",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            GameGridView(
                grid = playerGrid,
                modifier = Modifier.size(300.dp) // Size of the top grid
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Opponent's Grid (empty for attacks) - at the bottom
            Text(
                text = "Opponent's Board",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            RenderOpponentGrid(
                opponentGrid = opponentGrid,
                onCellClick = { row, col -> handleCellClick(row, col) }
            )
        }
    }

    // Function definitions below initial variables

    // Function to handle cell click during ship placement or attack


    // Method to place a ship on the grid.


    // Method to check if all opponent ships are destroyed.


    // Render the opponent's grid with ships masked.

}


@Composable
fun GameGridView(
    grid: List<List<String>>,
    modifier: Modifier = Modifier,
    onCellClick: ((Int, Int) -> Unit)? = null
) {
    Column(
        modifier = modifier.aspectRatio(1f)
    ) {
        for (i in grid.indices) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp) // Adds spacing between cells
            ) {
                for (j in grid[i].indices) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .background(
                                when (grid[i][j]) {
                                    "S" -> Color.Black // Ship
                                    "H" -> Color.Red   // Hit
                                    "M" -> Color.Gray  // Miss
                                    else -> Color.LightGray // Water
                                }
                            )
                            .clickable(enabled = onCellClick != null) {
                                onCellClick?.invoke(i, j)
                            }
                    )
                }
            }
        }
    }
}
