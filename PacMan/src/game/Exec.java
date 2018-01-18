package game;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import game.controllers.GhostController;
import game.controllers.Human;
import game.controllers.PacManController;
import game.core.G;
import game.core.GameView;
import game.core.Replay;
import game.core._G_;
import game.core._RG_;
import game.player.ghost.Legacy;
import game.player.pacman.PacmanGroup6;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/*
 * This class may be used to execute the game in timed or un-timed modes, with
 * or without visuals. Competitors should implement their controllers in
 * game.entries.ghosts and game.entries.pacman respectively. The skeleton
 * classes are already provided. The package structure should not be changed
 * (although you may create sub-packages in these packages).
 */
public class Exec {
	private GameView	gv		= null;
	private Thread		thread	= null;

	// Several options are listed - simply remove comments to use the option you
	// want
	public static void main(String[] args) {

		if (true) {
			LineChartContainer.open();
		} else {
			final PacmanGroup6 p = new PacmanGroup6();
			p.load("winner");
			final Exec exec = new Exec();
			exec.runGame(p, new Legacy(), true, G.DELAY);
		}

		// run game without time limits (un-comment if required)
		// exec.runGame(new RandomPacMan(),new RandomGhosts(),true,G.DELAY);

		// run game with time limits (un-comment if required)
		// exec.runGameTimed(new Human(),new AttractRepelGhosts(true),true);
		// run game with time limits. Here NearestPillPacManVS is chosen to
		// illustrate how to use graphics for debugging/information purposes
		// exec.runGameTimed(new game.player.pacman.Human(), new
		// AttractRepelGhosts(
		// false), true);

		// this allows you to record a game and replay it later. This could be
		// very useful when
		// running many games in non-visual mode - one can then pick out those
		// that appear irregular
		// and replay them in visual mode to see what is happening.
		// exec.runGameTimedAndRecorded(new Human(),new
		// AttractRepelGhosts(false),true,"human-v-Legacy2.txt");
		// exec.replayGame("human-v-Legacy2.txt");

		// this allows to select a player from GUI, the players must be save in
		// the package game.player.player and the ghosts in the package
		// game.player.ghosts
		// final Exec exec = new Exec();
		// exec.runGameMainFrame();
	}

	private static void trainPacman(Exec exec) {
		final int generationSize = 2000;
		final int trainingsPerNN = 5;
		final int generationCount = 500;
		final GhostController ghost = new Legacy();

		double highscore = Double.NEGATIVE_INFINITY;
		int highscoreGeneration = 0;
		PacmanGroup6 bestPacman = null;

		final double progressPerStep = 1d / (generationSize);

		/*
		 * Init an initial random generation
		 */
		List<PacmanGroup6> currentGeneration = new ArrayList<PacmanGroup6>(
				generationSize);
		PacmanGroup6 newPacman;
		for (int i = 0; i < generationSize; i++) {
			newPacman = new PacmanGroup6();
			newPacman.initRandom();
			currentGeneration.add(newPacman);
		}

		for (int gen = 0; gen < generationCount; gen++) {
			// for each new generation reset progress bar
			LineChartContainer.progress.setProgress(0);

			if ((gen + 1) % 100 == 0) {
				PacmanGroup6.baseDir = "D://tmp/Pacman/Gen" + (gen + 1);
				for (int i = 0; i < currentGeneration.size(); i++) {
					currentGeneration.get(i)
							.save(String.format("Pacman%04d", i));
				}
			}

			System.out.println(String.format("#### Generation %02d ####", gen));

			// score each individual (in parallel when possible)
			currentGeneration.parallelStream().forEach(pacman -> {
				pacman.setScore(exec.runExperiment(
						pacman,
						ghost,
						trainingsPerNN));
				LineChartContainer.progress.setProgress(
						LineChartContainer.progress.getProgress()
								+ progressPerStep);

			});

			// calculate cumulative scores for roulette wheel selection (this
			// needs to be done in order - no speedup with parallel execution)
			// also calculate some statistics
			double generationMax = Double.NEGATIVE_INFINITY;
			double generationMin = Double.POSITIVE_INFINITY;

			// sum of all scores
			int accumulatedScore = 0;
			for (final PacmanGroup6 pacman : currentGeneration) {
				accumulatedScore += pacman.score;
				pacman.setAccumulatedScore(accumulatedScore);

				if (pacman.score > highscore) {
					highscore = pacman.score;
					highscoreGeneration = gen;
					bestPacman = pacman;

					// save best so far
					PacmanGroup6.baseDir = "D://tmp/Pacman";
					bestPacman.save("winner");
				}

				generationMax = Math.max(generationMax, pacman.score);
				generationMin = Math.min(generationMin, pacman.score);
			}
			System.out.println(String.format(
					"Min:     %07.2f\n"
							+ "Average: %07.2f\n"
							+ "Max    : %07.2f\n",
					generationMin,
					(double) accumulatedScore / currentGeneration.size(),
					generationMax));

			LineChartContainer.updateOnPlatform(
					generationMin,
					generationMax,
					accumulatedScore / currentGeneration.size(),
					gen);

			// generate a new generation by selecting the fittest parents with
			// rouletteWheel selection
			final List<PacmanGroup6> newGeneration = new ArrayList<PacmanGroup6>(
					generationSize);
			for (int i = 0; i < generationSize / 2; i++) {
				PacmanGroup6[] pacmanPair = rouletteWheel(
						currentGeneration,
						accumulatedScore,
						2);
				pacmanPair = PacmanGroup6
						.createChilds(pacmanPair[0], pacmanPair[1]);

				newGeneration.add(pacmanPair[0]);
				newGeneration.add(pacmanPair[1]);
			}

			currentGeneration = newGeneration;
		}

		System.out.print(
				"\nFinished training for " + generationCount
						+ " Generations. Best Pacman is from Generating "
						+ highscoreGeneration + " and scored "
						+ bestPacman.score
						+ " Points.\nGeneration best replay from several runs...");

		PacmanGroup6.baseDir = "D://tmp/Pacman";
		bestPacman.save("winner");
	}

	/**
	 * Selects from this list with a probability based on the entities scores
	 * 
	 * @param scoredList
	 *            a list with already scored entities
	 * @param totalScore
	 *            the total score of all entities in this list
	 * @param count
	 *            the number of desired entities
	 * @return an array of size count with the chosen entities
	 */
	private static PacmanGroup6[] rouletteWheel(
			List<PacmanGroup6> scoredList,
			int totalScore,
			int count) {
		final PacmanGroup6[] resultArray = new PacmanGroup6[count];

		for (int i = 0; i < count; i++) {
			final double randomScore = Math.random() * totalScore;
			int targetIndex = scoredList.size() / 2;
			double indexStep = Math.ceil(scoredList.size() / 2.0);
			PacmanGroup6 curPacman;

			int tries = 0;
			while (tries < scoredList.size() / 2) {
				tries++;
				curPacman = scoredList.get(targetIndex);
				if (randomScore > curPacman.accumulatedScore) {
					// above range
					targetIndex += indexStep;
				} else if (randomScore < curPacman.accumulatedScore
						- curPacman.score) {
					// below range
					targetIndex -= indexStep;
				} else {
					// in range
					resultArray[i] = curPacman;
					break;
				}

				indexStep = Math.ceil(indexStep / 2.0);
				targetIndex = Math
						.min(scoredList.size() - 1, Math.max(0, targetIndex));
			}

			if (resultArray[i] == null) {
				// above algorithm bugged... use brute force :D
				System.err.println("Bug in roulette Wheel Selection...");
				for (final PacmanGroup6 cur : scoredList) {
					if (randomScore <= cur.accumulatedScore
							&& randomScore >= cur.accumulatedScore
									- cur.score) {
						// in range
						resultArray[i] = cur;
						break;
					}
				}
			}
		}

		return resultArray;
	}

	private void runGameMainFrame() {
		game = new _G_();
		game.newGame();

		gv = new GameView(game).showGame();
		gv
				.getMainFrame().getButton()
				.addActionListener(new java.awt.event.ActionListener() {
					@Override
					public void actionPerformed(
							java.awt.event.ActionEvent evt) {
						startButtonActionPerformed(evt);
					}
				});
	}

	private void startButtonActionPerformed(ActionEvent evt) {
		final PacManController pacManController = gv.getMainFrame()
				.getSelectedPacMan();
		final GhostController ghostController = gv.getMainFrame()
				.getSelectedGhost();

		if (thread != null && thread.isAlive()) {
			game.setGameOver(true);
			gv.getMainFrame().getButton().setText("Start");
			return;
		}

		final int trials = gv.getMainFrame().getTrials();
		if (trials > 1) {
			runExperiment(pacManController, ghostController, trials);
		}

		pacMan = new PacMan(pacManController);
		ghosts = new Ghosts(ghostController);

		if (gv.getMainFrame()
				.getSelectedPacMan() instanceof game.player.pacman.AbstractHuman) {
			final Human con = new Human();
			pacMan = new PacMan(con);
			gv.getMainFrame().getButton().addKeyListener(con);
		}

		if (game.gameOver()) {
			game.newGame();
		}

		this.thread = new Thread() {

			@Override
			public void run() {
				while (!game.gameOver()) {
					pacMan.alert();
					ghosts.alert();

					try {
						Thread.sleep(G.DELAY);
					} catch (final InterruptedException e) {
						e.printStackTrace();
					}

					game.advanceGame(pacDir, ghostDirs);

					gv.repaint();
				}
				pacMan.kill();
				ghosts.kill();
				gv.getMainFrame().getButton().setText("Start");
			}
		};
		this.thread.start();
		gv.getMainFrame().getButton().setText("Stop");
	}

	protected int		pacDir;
	protected int[]		ghostDirs;
	protected _G_		game;
	protected PacMan	pacMan;
	protected Ghosts	ghosts;
	protected boolean	pacmanPlayed, ghostsPlayed;

	/*
	 * For running multiple games without visuals. This is useful to get a good
	 * idea of how well a controller plays against a chosen opponent: the random
	 * nature of the game means that performance can vary from game to game.
	 * Running many games and looking at the average score (and standard
	 * deviation/error) helps to get a better idea of how well the controller is
	 * likely to do in the competition.
	 */
	public double runExperiment(
			PacManController pacManController,
			GhostController ghostController,
			int trials) {
		double avgScore = 0;
		double avgTime = 0;
		double avgPillsLeftover = 0;

		final _G_ gameTmp = new _G_();
		final int training = 1;

		for (int i = 0; i < trials; i++) {
			gameTmp.newGame();

			while (!gameTmp.gameOver() && gameTmp.getTotalTime() < 5000) {
				final long due = System.currentTimeMillis() + G.DELAY;
				gameTmp.advanceGame(
						pacManController.getAction(gameTmp.copy(), due),
						ghostController.getActions(gameTmp.copy(), due));
			}
			avgScore += gameTmp.getScore();
			avgTime += gameTmp.getTotalTime();
			avgPillsLeftover += gameTmp.getNumActivePills()
					+ gameTmp.getNumActivePowerPills() * 10;
			// System.out.println("Training "+training+++" Punkte:
			// "+gameTmp.getScore());
		}

		// System.out.println("Gesamtpunkte/Versuche: "+avgScore+"/"+trials+"
		// "+avgScore / trials);
		// return (avgScore - avgPillsLeftover * 10) / trials + 4000;
		return (avgScore) / trials;
	}

	/*
	 * Run game without time limit. Very good for testing as game progresses as
	 * soon as the controllers return their action(s). Can be played with and
	 * without visual display of game states. The delay is purely for visual
	 * purposes (as otherwise the game could be too fast if controllers compute
	 * quickly. For testing, this can be set to 0 for fasted game play.
	 */
	public void runGame(
			PacManController pacManController,
			GhostController ghostController,
			boolean visual,
			int delay) {
		game = new _G_();
		game.newGame();

		GameView gv = null;

		if (visual)
			gv = new GameView(game).showGame();

		while (!game.gameOver()) {
			final long due = System.currentTimeMillis() + G.DELAY;
			game.advanceGame(
					pacManController.getAction(game.copy(), due),
					ghostController.getActions(game.copy(), due));

			try {
				Thread.sleep(delay);
			} catch (final Exception e) {}

			if (visual)
				gv.repaint();
		}
	}

	/*
	 * Run game with time limit. This is how it will be done in the competition.
	 * Can be played with and without visual display of game states.
	 */
	public void runGameTimed(
			PacManController pacManController,
			GhostController ghostController,
			boolean visual) {
		game = new _G_();
		game.newGame();
		pacMan = new PacMan(pacManController);
		ghosts = new Ghosts(ghostController);

		GameView gv = null;

		if (visual) {
			gv = new GameView(game).showGame();

			if (pacManController instanceof game.player.pacman.AbstractHuman) {
				final Human con = new Human();
				pacMan = new PacMan(con);
				gv.getMainFrame().getButton().addKeyListener(con);
			}
		}

		while (!game.gameOver()) {
			pacMan.alert();
			ghosts.alert();

			try {
				Thread.sleep(G.DELAY);
			} catch (final InterruptedException e) {
				e.printStackTrace();
			}

			game.advanceGame(pacDir, ghostDirs);

			if (visual)
				gv.repaint();
		}

		pacMan.kill();
		ghosts.kill();
	}

	/*
	 * Runs a game and records all directions taken by all controllers - the
	 * data may then be used to replay any game saved using replayGame(-).
	 */
	public double runGameTimedAndRecorded(
			PacManController pacManController,
			GhostController ghostController,
			boolean visual,
			String fileName) {
		StringBuilder history = new StringBuilder();
		int lastLevel = 0;
		boolean firstWrite = false; // this makes sure the content of any
									// existing files is overwritten

		game = new _G_();
		game.newGame();
		pacMan = new PacMan(pacManController);
		ghosts = new Ghosts(ghostController);

		GameView gv = null;

		if (visual) {
			gv = new GameView(game).showGame();

			if (pacManController instanceof Human) {
				final Human con = new Human();
				pacMan = new PacMan(con);
				gv.getMainFrame().getButton().addKeyListener(con);
			}
		}

		while (!game.gameOver()) {
			pacMan.alert();
			ghosts.alert();

			try {
				Thread.sleep(G.DELAY);
			} catch (final InterruptedException e) {
				e.printStackTrace();
			}

			final int[] actionsTaken = game.advanceGame(pacDir, ghostDirs);

			if (visual)
				gv.repaint();

			history = addActionsToString(
					history,
					actionsTaken,
					game.getCurLevel() == lastLevel);

			// saves actions after every level
			if (game.getCurLevel() != lastLevel) {
				Replay.saveActions(history.toString(), fileName, firstWrite);
				lastLevel = game.getCurLevel();
				firstWrite = true;
				history = new StringBuilder();
			}
		}

		// save the final actions
		Replay.saveActions(history.toString(), fileName, firstWrite);

		pacMan.kill();
		ghosts.kill();

		return game.getScore();
	}

	/*
	 * This is used to replay a recorded game. The controllers are given by the
	 * class Replay which may also be used to load the actions from file.
	 */
	public void replayGame(String fileName) {
		final _RG_ game = new _RG_();
		game.newGame();

		final Replay replay = new Replay(fileName);
		final PacManController pacManController = replay.getPacMan();
		final GhostController ghostController = replay.getGhosts();

		final GameView gv = new GameView(game).showGame();

		while (!game.gameOver()) {
			game.advanceGame(
					pacManController.getAction(game.copy(), 0),
					ghostController.getActions(game.copy(), 0));

			gv.repaint();

			try {
				Thread.sleep(G.DELAY);
			} catch (final Exception e) {}
		}
	}

	private StringBuilder addActionsToString(
			StringBuilder history,
			int[] actionsTaken,
			boolean newLine) {
		history.append((game.getTotalTime() - 1)
				+ "\t" + actionsTaken[0] + "\t");

		for (int i = 0; i < G.NUM_GHOSTS; i++)
			history.append(actionsTaken[i + 1] + "\t");

		if (newLine)
			history.append("\n");

		return history;
	}

	// sets the latest direction to take for each game step (if controller
	// replies in time)
	public void setGhostDirs(int[] ghostDirs) {
		this.ghostDirs = ghostDirs;
		this.ghostsPlayed = true;
	}

	// sets the latest direction to take for each game step (if controller
	// replies in time)
	public void setPacDir(int pacDir) {
		this.pacDir = pacDir;
		this.pacmanPlayed = true;
	}

	/*
	 * Wraps the controller in a thread for the timed execution. This class then
	 * updates the directions for Exec to parse to the game.
	 */
	public class PacMan extends Thread {
		private final PacManController	pacMan;
		private boolean					alive;

		public PacMan(PacManController pacMan) {
			this.pacMan = pacMan;
			alive = true;
			start();
		}

		public synchronized void kill() {
			alive = false;
			notify();
		}

		public synchronized void alert() {
			notify();
		}

		@Override
		public synchronized void run() {
			while (alive) {
				try {
					synchronized (this) {
						wait();
					}

					setPacDir(pacMan.getAction(
							game.copy(),
							System.currentTimeMillis() + G.DELAY));
				} catch (final InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/*
	 * Wraps the controller in a thread for the timed execution. This class then
	 * updates the directions for Exec to parse to the game.
	 */
	public class Ghosts extends Thread {
		private final GhostController	ghosts;
		private boolean					alive;

		public Ghosts(GhostController ghosts) {
			this.ghosts = ghosts;
			alive = true;
			start();
		}

		public synchronized void kill() {
			alive = false;
			notify();
		}

		public synchronized void alert() {
			notify();
		}

		@Override
		public synchronized void run() {
			while (alive) {
				try {
					synchronized (this) {
						wait();
					}

					setGhostDirs(ghosts.getActions(
							game.copy(),
							System.currentTimeMillis() + G.DELAY));
				} catch (final InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static class LineChartContainer extends Application {

		final static Series<Number, Number>	minSeries		= new Series<>();
		final static Series<Number, Number>	averageSeries	= new Series<>();
		final static Series<Number, Number>	maxSeries		= new Series<>();

		static ProgressBar					progress		= null;

		public static void updateOnPlatform(
				double min,
				double max,
				double avg,
				double gen) {
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					LineChartContainer.minSeries.getData()
							.add(new Data<Number, Number>(gen, min));
					LineChartContainer.averageSeries.getData().add(
							new Data<Number, Number>(gen, avg));
					LineChartContainer.maxSeries.getData()
							.add(new Data<Number, Number>(gen, max));
				}
			});
		}

		@Override
		public void start(Stage stage) {
			progress = new ProgressBar(0);

			stage.setTitle("Line Chart Sample");
			// defining the axes
			final NumberAxis xAxis = new NumberAxis();
			final NumberAxis yAxis = new NumberAxis();
			xAxis.setLabel("Generation");
			// creating the chart
			final LineChart<Number, Number> lineChart = new LineChart<Number, Number>(
					xAxis, yAxis);

			lineChart.setTitle("Fitnessverlauf");
			// defining series
			minSeries.setName("min. Fitness");
			averageSeries.setName("avg. Fitness");
			maxSeries.setName("max. Fitness");

			final BorderPane root = new BorderPane();
			root.setCenter(lineChart);
			root.setBottom(progress);
			progress.setMaxWidth(Double.MAX_VALUE);

			final Scene scene = new Scene(root, 800, 600);
			lineChart.getData().add(minSeries);
			lineChart.getData().add(averageSeries);
			lineChart.getData().add(maxSeries);

			stage.setScene(scene);
			stage.show();

			final Exec exec = new Exec();

			final Task<Void> task = new Task<Void>() {
				@Override
				protected Void call() throws Exception {
					trainPacman(exec);
					return null;
				}
			};

			final Thread th = new Thread(task);
			th.setDaemon(true);
			th.start();
		}

		public static void open() {
			launch();
		}
	}
}