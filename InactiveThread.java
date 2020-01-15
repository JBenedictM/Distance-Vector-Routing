package cpsc441.a4.router;

public class InactiveThread implements Runnable{
	Router myRouter;
	String neighbourToPurge;
	
	public InactiveThread(Router myR, String neigbourName) {
		myRouter = myR;
		neighbourToPurge = neigbourName;
	}
	
	@Override
	public void run() {
		try {
			boolean result = myRouter.processInactiveNeighbour(neighbourToPurge);
			if (result) {
				System.out.println(neighbourToPurge + " router successfully deleted from tables");
			}
		} catch (Exception e) {
			System.out.println("Inactive Thread interrupted; expected");

		}
		
		
		
	}

}
