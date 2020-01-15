package cpsc441.a4.router;

public class KeepAliveThread implements Runnable{
	Router myRouter;

	public KeepAliveThread(Router aRouter) {
		myRouter = aRouter;
	}
	
	@Override
	public void run() {
		try {
			if (myRouter.keepAliveBroadcast()) {
				System.out.println("KeepAlive broadcast triggered and successful");
			}
			
		} catch (Exception e) {
			System.out.println("KeepAlive Thread interrupted; expected");
		}
	
		
	}
	
	
}
