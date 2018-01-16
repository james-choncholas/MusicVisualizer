import java.awt.Color;
import java.util.ArrayList; 
import java.util.Random; 
import ddf.minim.AudioInput;
import ddf.minim.Minim;
import ddf.minim.analysis.*;
import processing.core.*;

@SuppressWarnings("serial")
public class MusVisual extends PApplet{

	static Minim minim;
	static AudioInput audioIn;
	static FFT fftLin;
	static BeatDetect beat;
	static BeatListener bl;
	static Random rand;
	static float colorH;
	static float sat1;
	static float sat2;
	static float sat3;
	static float bright1;
	static float bright2;
	static float bright3;

	float radius = (float) 1.2;

	int numBucketsToUse;
	
	NavierStokesSolver fluidSolver;
	ArrayList<SimpleColorDot> scdList;

	//double visc = .000002;
	//double diff = .03;

	// super sluggist fluid
	//double visc = .02;
	//double diff = 100;
	
	double visc = .015;
	double diff = .05;
	float forceScale = 500;
	int maxFluidForce = 1;

	int numDotsPerEllipse = 20;
	float scale;
	int spectrumSweepSizeHzPerEllipse = 200;
	static int spectrumOffset = 13; //skip the bottom x buckets of the fft

	public void setup()
	{
		//////////////////INITIALIZE WINDOW////////////////////////////////////
		this.size(displayWidth, displayHeight,P3D);
		colorMode(HSB, 255);

		//////////////////INITIALIZE AUDIO PLAYER//////////////////////////////
		minim = new Minim(this);
		audioIn = minim.getLineIn(Minim.STEREO, 2048);

		/////////////////INITIALIZE FFT OBJECT/////////////////////////////////
		fftLin = new FFT( audioIn.bufferSize(), audioIn.sampleRate() );
		System.out.println("Total spectrum size: " + fftLin.getBandWidth() * fftLin.specSize() +" Hz");
		System.out.println("FFT bucket size: " + fftLin.getBandWidth() + " Hz");
		numBucketsToUse = (int)(spectrumSweepSizeHzPerEllipse / fftLin.getBandWidth());
		System.out.println("Number of fft buckets per semicircle side " + numBucketsToUse);

		/////////////////INITIALIZE BEAT DETECTOR//////////////////////////////
		beat = new BeatDetect(audioIn.bufferSize(), audioIn.sampleRate());
		beat.setSensitivity(10);
		bl = new BeatListener(beat, audioIn);

		/////////////////INITIALIZE FLUID AND PARTICLES////////////////////////
		scdList = new ArrayList<SimpleColorDot>();
		fluidSolver = new NavierStokesSolver();
		scdList.clear();
		colorH=0;

		rand = new Random();
	}


	
	public void draw()
	{
		background(0);

		//SET UP
		fftLin.forward( audioIn.mix );

		//set up fill color for fft bar
		colorH=(float) (colorH+.05); 
		if(colorH>225){
			colorH=0;
		}
		if(beat.isKick()){
			sat1 = 255;
			bright1 = 255;
		}else{
			sat1 = constrain((int) (sat1*.9),0,255);
			bright1 = constrain((int) (bright1*.9),70,255);
		}
		if(beat.isHat()){
			sat3 = 225;
			bright3 = 255;
		}else{
			sat3 = constrain((int) (sat3*.9),0,255);
			bright3 = constrain((int) (bright3*.9),70,255);
		}
		//Draw corner elipses
		//fill(255,0,bright1);
		//ellipse(0,0,800,800);
		//fill(colorH,0,bright3);
		//ellipse(displayWidth,displayHeight,800,800);
	    
		
		//Apply forces to fluid
		int n = NavierStokesSolver.N;
		int curBand;
		float cos;
		float sin;
		float dotColor;

		// Apply forces to the fluid
		for(int i = 0; i < numBucketsToUse; i ++) //Only draw first half of spec
		{
			cos = cos((float) ((i/(float)numBucketsToUse) *Math.PI/2.));
			sin = sin((float) ((i/(float)numBucketsToUse) *Math.PI/2.));
			curBand = (int) fftLin.getBand(i + spectrumOffset);

			if(curBand > maxFluidForce) curBand = maxFluidForce;
			fluidSolver.applyForce((int)(10*cos), (int)(20*sin), curBand * cos * forceScale, curBand * sin * forceScale);
		}

		for(int i = numBucketsToUse; i < numBucketsToUse*2; i ++) //Only draw second half of spec
		{
			cos = cos((float) ((i/(float) numBucketsToUse)*Math.PI/2.));
			sin = -sin((float) ((i/(float) numBucketsToUse)*Math.PI/2.));
			curBand = (int) fftLin.getBand(i + spectrumOffset);

			if(curBand > maxFluidForce) curBand = maxFluidForce;
			fluidSolver.applyForce((int)(n+10*cos), (int)(n+20*sin), curBand * cos * forceScale * 1.2, curBand * sin * forceScale);
		}


		// Add dots to the fluid	
		for(float i = rand.nextFloat(); i < numDotsPerEllipse; i ++) //Only draw first half of spec
		{
			cos = cos((float) ((i/(float)numDotsPerEllipse) *Math.PI/2.));
			sin = sin((float) ((i/(float)numDotsPerEllipse) *Math.PI/2.));
			dotColor = map(i, 0, numDotsPerEllipse, 0, (float) .5);
			scdList.add( new SimpleColorDot(400*cos, 400*sin, radius, dotColor, this));
		}

		for(float i = numDotsPerEllipse + rand.nextFloat(); i < numDotsPerEllipse*2; i ++) //Only draw second half of spec
		{
			cos = cos((float) ((i/(float) numDotsPerEllipse)*Math.PI/2.));
			sin = -sin((float) ((i/(float) numDotsPerEllipse)*Math.PI/2.));
			dotColor = map(i, numDotsPerEllipse, 2*numDotsPerEllipse, (float).5, (float) .9);
			scdList.add( new SimpleColorDot(width+400*cos, height+400*sin, radius, dotColor, this));
		}
		double dt = 1 / frameRate;
		fluidSolver.tick(dt, visc, diff);
		
		//apply forces to dots
		int cellX;
		int cellY;
		SimpleColorDot curDot;
		for (int i = 0; i < scdList.size(); i++) {
			curDot = scdList.get(i);
			cellX = floor(curDot.pos.x / (width/n));
			cellY = floor(curDot.pos.y / (height/n));
			cellX = max(cellX, 0);
			cellY = min(cellY, n-1);
			float dx = (float) fluidSolver.getDx(cellX, cellY);
			float dy = (float) fluidSolver.getDy(cellX, cellY);
			curDot.applyForce(new PVector(dx*10, dy*10));
			curDot.run();
			
			if (curDot.checkDead() == true) {
				scdList.remove(i);
			}
		}

//		stroke(color(216));
//	    paintGrid();	 
//	    float scale = 100;
//	    stroke(color(255));
//	    paintMotionVector(scale);
	}

	private void paintGrid() {
	    int n = NavierStokesSolver.N;
	    float cellHeight = height / n;
	    float cellWidth = width / n;
	    for (int i = 1; i < n; i++) {
	        line(0, cellHeight * i, width, cellHeight * i);
	        line(cellWidth * i, 0, cellWidth * i, height);
	    }
	}

	private void paintMotionVector(float scale) {
	    int n = NavierStokesSolver.N;
	    float cellHeight = height / n;
	    float cellWidth = width / n;
	    for (int i = 0; i < n; i++) {
	        for (int j = 0; j < n; j++) {
	            float dx = (float) fluidSolver.getDx(i, j);
	            float dy = (float) fluidSolver.getDy(i, j);
	 
	            float x = cellWidth / 2 + cellWidth * i;
	            float y = cellHeight / 2 + cellHeight * j;
	            dx *= scale;
	            dy *= scale;
	 
	            line(x, y, x + dx, y + dy);
	        }
	    }
	}
}
