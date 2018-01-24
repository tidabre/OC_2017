package game.player.ghost;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Random;

import game.core.Game;
import game.core.Game.DM;
import gui.AbstractGhost;

public class GhostGroup6 extends AbstractGhost {

	/**
	 * Base directory to store pacman data
	 */
	static public String			baseDir					= "D://tmp/Pacman";

	/**
	 * The Bias value to be used
	 */
	private static final float		BIAS					= 1f;

	/**
	 * The radius of all randomly generated weight values.
	 */
	private static final float		WEIGHT_RADIUS			= 4f;

	/**
	 * Whether or not the output should be checked for validity.
	 */
	private static final boolean	CHECK_DIRECTION			= false;
	/**
	 * Apply this factor, to get some kind of normalized distance
	 */
	public static final float		DIST_CORRECTION			= 100f;

	/**
	 * The distance, ghosts can "look"
	 */
	public static final float		SIGHT_RANGE				= 100f;

	private final static Random		RAND					= new Random(
			System.currentTimeMillis());

	// public static final double MUTATION_PROBABILITY = 0.001;
	// public static final double MUTATION_PROBABILITY = 0.01;
	public static double			BASE_MUTATION_RATE		= 0.0015;

	private static final int		INPUT_COUNT				= 60;

	private static final int		OUTPUT_COUNT			= 4
			* Game.NUM_GHOSTS;

	/**
	 * Rule of Thumb: 1 Hidden layer, number of perceptrons: mean of #inputs and
	 * #outputs
	 */
	private static final int		HIDDEN_PERCEPTRONS		= (INPUT_COUNT
			+ OUTPUT_COUNT) / 2;												// /
																				// 2;

	public float[][]				hiddenWeights;

	public float[][]				outputWeights;

	/**
	 * Stores the score of this Instance
	 */
	public double					score					= 0;

	/**
	 * Stores score information of previous Instances for the GA-Algorithm
	 */
	public double					accumulatedInverseScore	= 0;

	private float[]					inputValues;

	/**
	 * Used to store perceptron values when calling output.
	 */
	private final float[]			hiddenPerceptronValues;

	/**
	 * Used to store perceptron values when calling output.
	 */
	private final float[]			outputPerceptronValues;

	/**
	 * Creates exactly two new {@link GhostGroup6} instances
	 * 
	 * @param mother
	 * @param father
	 * @return the array of child instances with length 2
	 */
	public static GhostGroup6[] createChilds(
			GhostGroup6 mother,
			GhostGroup6 father,
			double mutationRate) {
		final GhostGroup6[] childs = {
				new GhostGroup6(false), new GhostGroup6(false)
		};

		final int splitPointHiddenWeights = RAND.nextInt(INPUT_COUNT + 1);

		final int lenHidden = childs[0].hiddenWeights.length;
		final int lenHiddenWeights = childs[0].hiddenWeights[0].length;

		for (int i = 0; i < lenHidden; i++) {
			for (int w = 0; w < lenHiddenWeights; w++) {
				/*
				 * check, if the weight should be mutated
				 */
				if (RAND.nextDouble() < mutationRate) {
					/*
					 * shift range to [-4,4]
					 */
					childs[0].hiddenWeights[i][w] = RAND.nextFloat() * 2
							* WEIGHT_RADIUS - WEIGHT_RADIUS;
					childs[1].hiddenWeights[i][w] = RAND.nextFloat() * 2
							* WEIGHT_RADIUS - WEIGHT_RADIUS;
				} else {
					/*
					 * in case of no mutation, just copy
					 */
					if (i < splitPointHiddenWeights) {
						childs[0].hiddenWeights[i][w] = mother.hiddenWeights[i][w];
						childs[1].hiddenWeights[i][w] = father.hiddenWeights[i][w];
					} else {
						childs[0].hiddenWeights[i][w] = father.hiddenWeights[i][w];
						childs[1].hiddenWeights[i][w] = mother.hiddenWeights[i][w];
					}
				}
			}
		}

		final int splitPointOutputWeights = RAND
				.nextInt(HIDDEN_PERCEPTRONS + 1);

		final int lenOutput = childs[0].outputWeights.length;;
		final int lenOutputWeights = childs[0].outputWeights[0].length;

		for (int i = 0; i < lenOutput; i++) {
			for (int w = 0; w < lenOutputWeights; w++) {
				/*
				 * check, if the weight should be mutated
				 */
				if (RAND.nextDouble() < BASE_MUTATION_RATE) {/*
																 * shift range
																 * to [-4,4]
																 */
					childs[0].outputWeights[i][w] = RAND.nextFloat() * 2
							* WEIGHT_RADIUS - WEIGHT_RADIUS;
					childs[0].outputWeights[i][w] = RAND.nextFloat() * 2
							* WEIGHT_RADIUS - WEIGHT_RADIUS;
				} else {
					/*
					 * in case of no mutation, just copy
					 */
					if (i < splitPointOutputWeights) {
						childs[0].outputWeights[i][w] = mother.outputWeights[i][w];
						childs[1].outputWeights[i][w] = father.outputWeights[i][w];
					} else {
						childs[0].outputWeights[i][w] = father.outputWeights[i][w];
						childs[1].outputWeights[i][w] = mother.outputWeights[i][w];
					}
				}
			}
		}

		return childs;
	}

	public GhostGroup6() {
		this(true);
	}

	public GhostGroup6(boolean loadBest) {
		// one weight set/vector for each hidden perceptron (fully
		// connected)
		// +1 for the bias input to each neuron
		hiddenWeights = new float[HIDDEN_PERCEPTRONS][INPUT_COUNT + 1];

		// one weight vector for each output
		outputWeights = new float[OUTPUT_COUNT][HIDDEN_PERCEPTRONS + 1];

		hiddenPerceptronValues = new float[HIDDEN_PERCEPTRONS];
		outputPerceptronValues = new float[OUTPUT_COUNT];

		if (loadBest) {
			load(
					getClass().getClassLoader()
							.getResourceAsStream("data/Group6DataForLevel2"));
		}
	}

	/**
	 * Random initialization of all weights in this network.
	 */
	public void initRandom() {
		/*
		 * Make use of the pow function to reduce probability for weight values
		 * close to 1. TODO: change?
		 */
		for (int i = 0; i < hiddenWeights.length; i++) {
			for (int j = 0; j < INPUT_COUNT + 1; j++) {
				hiddenWeights[i][j] = (float) (RAND.nextDouble() * 2
						* WEIGHT_RADIUS) - WEIGHT_RADIUS;
			}
		}

		for (int i = 0; i < outputWeights.length; i++) {
			for (int j = 0; j < hiddenWeights.length + 1; j++) {
				outputWeights[i][j] = (float) (RAND.nextDouble() * 2
						* WEIGHT_RADIUS) - WEIGHT_RADIUS;
			}
		}
	}

	@Override
	public int[] getActions(Game game, long timeDue) {
		return output(game);
	}

	@Override
	public String getGhostGroupName() {
		// TODO Auto-generated method stub
		return "GhostGroup6";
	}

	public void save(String name) {
		try {
			new File(baseDir).mkdirs();
			final ObjectOutputStream objStream = new ObjectOutputStream(
					Files.newOutputStream(
							Paths.get(baseDir, name)));

			objStream.writeObject(hiddenWeights);
			objStream.writeObject(outputWeights);
			objStream.flush();
			objStream.close();
		} catch (final IOException e) {
			e.printStackTrace();
			System.err.println("Could not save object: " + this);
		}
	}

	public void load(String name) {
		try {
			load(Files.newInputStream(
					Paths.get(baseDir, name),
					StandardOpenOption.READ));

		} catch (final IOException e) {
			e.printStackTrace();
			System.err.println("Could not load object: " + this);
		}
	}

	public void load(InputStream stream) {
		try {
			final ObjectInputStream objStream = new ObjectInputStream(
					stream);

			hiddenWeights = (float[][]) objStream.readObject();
			outputWeights = (float[][]) objStream.readObject();
			objStream.close();
		} catch (final IOException e) {
			e.printStackTrace();
			System.err.println("Could not load object: " + this);
		} catch (final ClassNotFoundException e) {
			e.printStackTrace();
			System.err.println("Data format is corrupt.");
		}
	}

	/**
	 * Outputs the directions with the highest values generated by the network.
	 * 
	 * @return the desired directions (which may be invalid, since they are not
	 *         checked)
	 */
	public int[] output(Game game) {
		if (inputValues == null)
			inputValues = calculateInputs(game, null);
		else
			calculateInputs(game, inputValues);

		float pValue;
		for (int i = 0; i < hiddenPerceptronValues.length; i++) {
			// reset perceptronValue
			pValue = 0;

			/*
			 * for each weight and input add the product to our hidden
			 * perceptron output
			 */
			pValue += hiddenWeights[i][0] * BIAS;
			for (int w = 1; w < hiddenWeights[i].length; w++) {
				pValue += hiddenWeights[i][w] * inputValues[w - 1];
			}

			pValue = sigmoid(pValue);

			hiddenPerceptronValues[i] = pValue;
		}

		for (int i = 0; i < outputPerceptronValues.length; i++) {
			// reset perceptronValue
			pValue = 0;

			/*
			 * for each weight and input add the product to our hidden
			 * perceptron output use offsets to calculate bias first at position
			 * 0
			 */
			pValue += outputWeights[i][0] * BIAS;
			for (int w = 1; w < outputWeights[i].length; w++) {
				pValue += outputWeights[i][w] * hiddenPerceptronValues[w - 1];
			}

			pValue = sigmoid(pValue);

			outputPerceptronValues[i] = pValue;
		}

		// find final direction outputs now
		final float[] maxResults = new float[Game.NUM_GHOSTS];
		maxResults[0] = Float.NEGATIVE_INFINITY;
		maxResults[1] = Float.NEGATIVE_INFINITY;
		maxResults[2] = Float.NEGATIVE_INFINITY;
		maxResults[3] = Float.NEGATIVE_INFINITY;

		// auto init to 0
		final int[] resultDirections = new int[Game.NUM_GHOSTS];

		for (int i = 0; i < outputPerceptronValues.length; i++) {
			if (outputPerceptronValues[i] > maxResults[i / Game.NUM_GHOSTS]) {
				if (!CHECK_DIRECTION || directionValid(
						i % 4,
						game.getPossibleGhostDirs(i / Game.NUM_GHOSTS))) {
					maxResults[i / Game.NUM_GHOSTS] = outputPerceptronValues[i];
					resultDirections[i / Game.NUM_GHOSTS] = i % 4;
				}
			}
		}

		return resultDirections;
	}

	boolean directionValid(int direction, int[] allowedDirections) {
		for (final int dir : allowedDirections)
			if (dir == direction)
				return true;

		return false;
	}

	/**
	 * Returns the inputs for this Pacman Neural-Net. Creates a new array of
	 * matching size, if inputArray is empty.
	 * 
	 * @param inputArray
	 *            the array to be filled with values
	 * @return inputArray or a newly created float array, if inputArray was null
	 */
	float[] calculateInputs(Game game, float[] inputArray) {

		/*
		 * If non-existant, create a new Array. Else clear existing one.
		 */
		if (inputArray == null)
			inputArray = new float[INPUT_COUNT];
		else
			Arrays.fill(inputArray, 0);
		/*
		 * Continuously increases the array index count
		 */
		int index = 0;

		/*
		 * Store for performance - this will spare some method calls
		 */
		final int pacmanLocation = game.getCurPacManLoc();

		/*
		 * Measure, in which directions the pacman may go. Possible directions
		 * are set to 1. Otherwise, the value will stay at 0
		 */
		for (final int direction : game.getPossiblePacManDirs(true)) {
			switch (direction) {
				case Game.LEFT :
					inputArray[index] = 1;
					break;
				case Game.RIGHT :
					inputArray[index + 1] = 1;
					break;
				case Game.UP :
					inputArray[index + 2] = 1;
					break;
				case Game.DOWN :
					inputArray[index + 3] = 1;
					break;
			}
		}
		index += 4;

		for (int ghost = 0; ghost < Game.NUM_GHOSTS; ghost++) {
			final int curGhostLoc = game.getCurGhostLoc(ghost);
			final int curGhostDir = game.getCurGhostDir(ghost);

			final int toPacmanDir = game
					.getNextGhostDir(ghost, pacmanLocation, true, DM.PATH);

			// ghost left
			inputArray[index++] = toPacmanDir == Game.LEFT ? 1 : 0;
			// ghost right
			inputArray[index++] = toPacmanDir == Game.RIGHT ? 1 : 0;
			// ghost above
			inputArray[index++] = toPacmanDir == Game.UP ? 1 : 0;
			// ghost below
			inputArray[index++] = toPacmanDir == Game.DOWN ? 1 : 0;

			// ghost currently moves to left
			inputArray[index++] = curGhostDir == Game.LEFT ? 1 : 0;
			// ghost currently moves to right
			inputArray[index++] = curGhostDir == Game.RIGHT ? 1 : 0;
			// ghost currently moves to top
			inputArray[index++] = curGhostDir == Game.UP ? 1 : 0;
			// ghost currently moves to bottom
			inputArray[index++] = curGhostDir == Game.DOWN ? 1 : 0;

			// ghost distance to pacman
			inputArray[index++] = (SIGHT_RANGE - game
					.getPathDistance(pacmanLocation, curGhostLoc))
					/ DIST_CORRECTION;
			// ghost edible
			inputArray[index++] = game.getEdibleTime(ghost) > 0 ? 1 : 0;

			/*
			 * Measure, in which directions the ghost is allowed to go. Possible
			 * directions are set to 1. Otherwise, the value will stay at 0
			 */
			for (final int direction : game.getPossibleGhostDirs(ghost)) {
				switch (direction) {
					case Game.LEFT :
						inputArray[index] = 1;
						break;
					case Game.RIGHT :
						inputArray[index + 1] = 1;
						break;
					case Game.UP :
						inputArray[index + 2] = 1;
						break;
					case Game.DOWN :
						inputArray[index + 3] = 1;
						break;
				}
			}
			index += 4;
		}

		return inputArray;
	}

	float sigmoid(float x) {
		return (float) (1 / (1 + Math.exp(-x)));
	}

	public double getScore() {
		return score;
	}

	public void setScore(double score) {
		this.score = score;
	}

	public double getAccumulatedScore() {
		return accumulatedInverseScore;
	}

	public void setAccumulatedScore(double accumulatedScore) {
		this.accumulatedInverseScore = accumulatedScore;
	}
}
