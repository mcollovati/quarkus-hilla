import { AutoCrud } from '@hilla/react-crud';
import { UserService } from 'Frontend/generated/endpoints';
import UserModel from 'Frontend/generated/com/example/application/autocrud/UserModel';

export default function AutoCrudView() {
    return <AutoCrud service={UserService} model={UserModel} />;
}


