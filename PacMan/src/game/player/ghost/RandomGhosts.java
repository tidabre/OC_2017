package game.player.ghost;

import game.core.G;
import game.core.Game;
import gui.AbstractGhost;

public final class RandomGhosts extends AbstractGhost {
	double distance = Double.NEGATIVE_INFINITY;
	@Override
	public int[] getActions(Game game, long timeDue) {

		double curDist = game.getPathDistance(
				game.getCurPacManLoc(),
				game.getCurGhostLoc(0));
		if (curDist > distance) {
			distance = curDist;
			System.out.println(distance);
		}
		curDist = game.getPathDistance(
				game.getCurPacManLoc(),
				game.getCurGhostLoc(1));
		if (curDist > distance) {
			distance = curDist;
			System.out.println(distance);
		}
		curDist = game.getPathDistance(
				game.getCurPacManLoc(),
				game.getCurGhostLoc(2));
		if (curDist > distance) {
			distance = curDist;
			System.out.println(distance);
		}
		curDist = game.getPathDistance(
				game.getCurPacManLoc(),
				game.getCurGhostLoc(3));
		if (curDist > distance) {
			distance = curDist;
			System.out.println(distance);
		}
		final int[] directions = new int[Game.NUM_GHOSTS];

		// Chooses a random LEGAL action if required. Could be much simpler by
		// simply returning
		// any random number of all of the ghosts
		for (int i = 0; i < directions.length; i++)
			if (game.ghostRequiresAction(i)) {
				final int[] possibleDirs = game.getPossibleGhostDirs(i);
				directions[i] = possibleDirs[G.rnd
						.nextInt(possibleDirs.length)];
			}

		return directions;
	}

	@Override
	public String getGhostGroupName() {
		return "Random Ghost - Gruppe 1";
	}
}