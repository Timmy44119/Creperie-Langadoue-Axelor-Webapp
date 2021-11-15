package bzh.toolapp.apps.remisecascade.web;

import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;

@Singleton
public class InvoiceLineController {
  public void compute(ActionRequest request, ActionResponse response) {
    System.out.println("MAIS JE SUIS UN GROS TEUBE");
  }
}
