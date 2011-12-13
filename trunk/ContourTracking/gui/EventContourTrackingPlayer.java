import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JSlider;
import javax.swing.JOptionPane;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.WindowConstants;
import javax.swing.JFileChooser;
import javax.swing.filechooser.*;
import javax.swing.event.*;

public class EventContourTrackingPlayer implements ActionListener, ChangeListener {
	class PlayerTask extends TimerTask {
		EventContourTrackingPlayer fPlayer;
		PlayerTask(EventContourTrackingPlayer player) {
			fPlayer = player;
		}

		public void run() {
			fPlayer.scheduleNextFrame();
		}
	}

	class GridGraph extends JPanel {
		EventContourTrackingPlayer fPlayer;

		GridGraph(EventContourTrackingPlayer player) {
			fPlayer = player;
		}

		protected void paintComponent(Graphics g) {
			ContourTracking.Snapshot snapshot = fPlayer.getPlayingSnapshot();
			if(snapshot == null)
				return;

			snapshot.debug();
			// draw canvas
			Graphics2D g2d = (Graphics2D)g;
			g2d.setColor(Color.BLACK);
			g2d.fillRect(0, 0, getWidth(), getHeight());

			// compute colors and draw motes
			int DIM = snapshot.getGridDimension();
			int centerX = getWidth() / 2;
			int centerY = getHeight() / 2;
			int marginX = getWidth() / 5;
			int marginY = getHeight() / 100 * 20;
			int gridX = marginX;
			int gridY = marginY;
			int gridWidth = getWidth() - 2 * marginX;
			int gridHeight = getHeight() - 2 * marginY;

			/* Bluff to draw squares - Vivek*/
			int offsetX = gridWidth / (DIM - 1);
			int offsetY = gridHeight / (DIM - 1);
			int radius = 15;

			// draw grid lines
			float []f={ 10f, 10f };
			Stroke stroke = g2d.getStroke();
			g2d.setStroke(new BasicStroke(0, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0F, f, 0f)); 
			g2d.setColor(Color.WHITE);
			g2d.drawLine(gridX, gridY, gridX+gridWidth, gridY);
			g2d.drawLine(gridX, gridY, gridX, gridY+gridHeight);
			g2d.drawLine(gridX+gridWidth, gridY+gridHeight, gridX, gridY+gridHeight);
			g2d.drawLine(gridX+gridWidth, gridY+gridHeight, gridX+gridWidth, gridY);
			for(int i  = 1; i < DIM - 1; i++) {
				g2d.drawLine(gridX + (i * gridWidth / (DIM-1)), gridY, gridX + (i * gridWidth / (DIM-1)), gridY + gridHeight);
				g2d.drawLine(gridX, gridY + (i * gridHeight / (DIM-1)), gridX + gridWidth, gridY + (i * gridHeight / (DIM-1)));
			}
			g2d.setStroke(stroke);


			//for (int i = 0; i < (count > 16 ? 16 : count) ; i++) {
			for(Map.Entry<Integer, ContourTracking.Mote> entry: snapshot.entrySet()) {
				boolean same = true;
				int id = entry.getKey().intValue();
				ContourTracking.Mote mote = entry.getValue();

				ContourTracking.Mote nbr = snapshot.getMote(snapshot.getMoteNeighborId(id, ContourTracking.Position.L));
				if(nbr != null) 
					same = (mote.isAboveThreshold() == nbr.isAboveThreshold());
				
				if(same && (nbr = snapshot.getMote(snapshot.getMoteNeighborId(id, ContourTracking.Position.R))) != null) 
					same = (mote.isAboveThreshold() == nbr.isAboveThreshold());

				if(same && (nbr = snapshot.getMote(snapshot.getMoteNeighborId(id, ContourTracking.Position.B))) != null) 
					same = (mote.isAboveThreshold() == nbr.isAboveThreshold());
				
				if(same && (nbr = snapshot.getMote(snapshot.getMoteNeighborId(id, ContourTracking.Position.U))) != null) 
					same = (mote.isAboveThreshold() == nbr.isAboveThreshold());

				mote.setColor(same ? (mote.isAboveThreshold() ? Color.WHITE : Color.BLACK) : Color.GRAY);
				int idx = snapshot.getMoteIndex(id);
				int row = idx / DIM;
				int col = idx % DIM;
				int moteX = gridX + col * offsetX;
				int moteY = gridY + gridHeight - row * offsetY;

				//System.out.printf("mote[%d] idx: %d, sample: %d, color: %s\n", id, idx, mote.getSample(), mote.getColor());
		
				// Draw circles for contour - Vivek
				/*
				Ellipse2D.Double circle;
				double circleR;
				if(mote[2] == 1) {
					circleR = ( MAXREAD - (parent.parent.threshold - mote[1] ) ) / (2*MAXREAD);
					circleR = circleR * (gridHeight/3);  
					clipped.setColor(new Color(153,153,255));
					circle=new Ellipse2D.Double(moteX-circleR, moteY-circleR, 2*circleR, 2*circleR);
					//System.out.println("R = "+ circleR);
					clipped.fill(circle);
				}
				*/
				//End - Vivek

				// draw mote on the grid
				if(mote.getColor() == Color.BLACK) { // black
					g2d.setColor(mote.getColor());
					g2d.fillRect(moteX-radius, moteY-radius, 2*radius, 2*radius);
					g2d.setColor(Color.WHITE);
					g2d.drawRect(moteX-radius, moteY-radius, 2*radius, 2*radius);
					g2d.setColor(Color.WHITE);
					g2d.drawString(String.valueOf(id), moteX-3, moteY+4);
				} else { // white or gray
					g2d.setColor(mote.getColor());
					g2d.fillRect(moteX-radius, moteY-radius, 2*radius, 2*radius);
					g2d.setColor(mote.getColor() == Color.WHITE ? Color.BLACK : Color.RED);
					g2d.drawString(String.valueOf(id), moteX-3, moteY+4);
				}
			}
		}
	}

	Vector<ContourTracking.Snapshot> fSnapshots;
	ContourTracking.Snapshot fPlayingSnapshot;
	Timer fTimer;
	boolean fSliderChangeEnabled;

	JFrame fFrame;
	JPanel fScreen;
	JButton fPlayBtn;
	JSlider fPlaybackSlider;
	GridGraph fGridGraph;
	Bulletin fBulletin;

	EventContourTrackingPlayer() {
		fSnapshots = new Vector<ContourTracking.Snapshot>();
		fSliderChangeEnabled = true;
		setupUI();
	}

	void showEventString(String text) {
		fBulletin.setText(text);
	}

	double getDuration() {
		return (fSnapshots.lastElement().getLatestSampleTimestamp() - fSnapshots.firstElement().getLatestSampleTimestamp()) / 1000.0;
	}

	boolean load(File file) {
		return load(file.getPath());
	}

	boolean load(String filename) {
		ObjectInputStream in = null;
		try {
			in = new ObjectInputStream(new FileInputStream(filename));
		} catch(IOException e) {
			e.printStackTrace();
			return false;
		}

		stop();
		ContourTracking.Snapshot snapshot = null;
		fPlayingSnapshot = null;
		fSnapshots.clear();
		boolean ret = false;
		try {
			while((snapshot = (ContourTracking.Snapshot)in.readObject()) != null)
				fSnapshots.add(snapshot);
		} catch(ClassNotFoundException e) {
			e.printStackTrace();
			ret = false;
		} catch(EOFException e) {
			ret = true;
		} catch(IOException e) {
			e.printStackTrace();
			ret = false;
		} finally {
			try {
				in.close();
			} catch(IOException e) {
				e.printStackTrace();
			}
		}

		if(fSnapshots.isEmpty())
			return ret;

		double duration = (fSnapshots.lastElement().getLatestSampleTimestamp() - fSnapshots.firstElement().getLatestSampleTimestamp()) / 1000.0;
		fPlaybackSlider.setMajorTickSpacing(3);
		fPlaybackSlider.setMinorTickSpacing(1);
		fPlaybackSlider.setPaintTicks(true);
		fPlaybackSlider.setPaintLabels(true);
		fPlaybackSlider.setValue(0);
		fPlaybackSlider.setMinimum(0);
		fPlaybackSlider.setMaximum(duration > (int)duration ? (int)duration + 1 : (int) duration);
		System.out.println("Number of snapshots: " + fSnapshots.size());
		System.out.println("Event Contour Video duration: " + duration + " seconds");
		return ret;
	}

	void dump() {
		for(ContourTracking.Snapshot snapshot: fSnapshots)
			snapshot.debug();
	}

	void setupUI() {
			JPanel main = new JPanel(new BorderLayout());
			main.setMinimumSize(new Dimension(640, 480));
			main.setPreferredSize(new Dimension(800, 600));
			main.add(fBulletin = new Bulletin(), BorderLayout.NORTH);
			main.add(fGridGraph = new GridGraph(this), BorderLayout.CENTER);

			fPlayBtn = new JButton("Open");
			fPlayBtn.addActionListener(this);
			fPlaybackSlider = new JSlider(JSlider.HORIZONTAL, 0, 0, 0);
			fPlaybackSlider.addChangeListener(this);
			Box controls = new Box(BoxLayout.X_AXIS);
			controls.add(fPlayBtn);
			controls.add(fPlaybackSlider);
			main.add(controls, BorderLayout.SOUTH);

			fFrame = new JFrame("Event Contour Tracking Player");
			fFrame.setSize(main.getPreferredSize());
			fFrame.getContentPane().add(main);
			fFrame.setLocationRelativeTo(null);
			fFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			//fFrame.addWindowListener(new WindowAdapter() {
				//public void windowClosing(WindowEvent e) { System.exit(0); }
			//});
	}

	void show() {
		fFrame.setVisible(true);
	}

	ContourTracking.Snapshot getPlayingSnapshot() {
		return fPlayingSnapshot;
	}

	ContourTracking.Snapshot getNextPlayingSnapshot() {
		if(fSnapshots.isEmpty() || fPlayingSnapshot == fSnapshots.lastElement())
			return null;

		if(fPlayingSnapshot == null)
				return fSnapshots.firstElement();

		return fSnapshots.get((fSnapshots.indexOf(fPlayingSnapshot) + 1));
	}

	void setSlierChangeEnabled(boolean enabled) {
		fSliderChangeEnabled = enabled;
	}

	private boolean scheduleNextFrame() {
		fPlayingSnapshot = getNextPlayingSnapshot();
		ContourTracking.Snapshot nextPlayingSnapshot = getNextPlayingSnapshot();
		if(fPlayingSnapshot == null) 
			return false;

		long t1 = fSnapshots.firstElement().getLatestSampleTimestamp();
		long t2 = fPlayingSnapshot.getLatestSampleTimestamp();
		double time = (t2 - t1) / 1000.0;
		setSlierChangeEnabled(false);
		fPlaybackSlider.setValue((int)time);
		if(nextPlayingSnapshot == null) {
			fPlaybackSlider.setValue(fPlaybackSlider.getMaximum());
		} else {
			fTimer = new Timer();
			fTimer.schedule(new PlayerTask(this), nextPlayingSnapshot.getLatestSampleTimestamp() - fPlayingSnapshot.getLatestSampleTimestamp());
		}

		setSlierChangeEnabled(true);
		fGridGraph.repaint();
		fBulletin.setText(fPlayingSnapshot.getEvent() == null? "No Event" : fPlayingSnapshot.getEvent().toString());
		return true;
	}

	void play() {
		stop();
		scheduleNextFrame();
	}

	void stop() {
		fPlayingSnapshot = null;
		if(fTimer != null)
			fTimer.cancel();
	}

	ContourTracking.Snapshot seek(int sec) {
		if(fSnapshots.isEmpty())
			return null;

		if(sec < 0)
			sec = 0;

		long pos = fSnapshots.firstElement().getLatestSampleTimestamp() + sec * 1000;
		for(int i = 0; i < fSnapshots.size() - 1; i++) {
			if(pos >= fSnapshots.get(i).getLatestSampleTimestamp() && pos < fSnapshots.get(i+1).getLatestSampleTimestamp())
				fPlayingSnapshot = (i - 1) < 0 ? null : fSnapshots.get(i-1);
		}

		if(pos >= fSnapshots.lastElement().getLatestSampleTimestamp())
			fPlayingSnapshot = fSnapshots.get(fSnapshots.size()-1);

		return fPlayingSnapshot;
	}

	public void actionPerformed(ActionEvent e) {
		JButton source = (JButton)e.getSource();
		JFileChooser fc = new JFileChooser("./");
		fc.addChoosableFileFilter(new FileNameExtensionFilter("Event Contour Tracking file", "ect", "ect"));
		if(fc.showOpenDialog(fFrame) == JFileChooser.APPROVE_OPTION) {
			File ect = fc.getSelectedFile();
			if(load(ect) && !fSnapshots.isEmpty()) {
				play();
			} else {
				JOptionPane.showMessageDialog(fFrame, "ERROR: fail to load or there is nothing to play in " + ect.getName());
				//System.out.println("));
			}
		}
	}

	public void stateChanged(ChangeEvent e) {
		JSlider source = (JSlider)e.getSource();
		if (!source.getValueIsAdjusting() && fSliderChangeEnabled) {
			seek(source.getValue());
			play();
		}
	}

	static public void main(String[] args) {
		EventContourTrackingPlayer player = new EventContourTrackingPlayer();
		player.show();
	}
}
