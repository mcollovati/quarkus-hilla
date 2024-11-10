import { AutoGrid } from '@vaadin/hilla-react-crud';
import { UserService } from 'Frontend/generated/endpoints';
import UserModel from 'Frontend/generated/com/example/application/autocrud/UserModel';

export default function AutoGridView() {
    return <AutoGrid service={UserService} model={UserModel} />;
}


